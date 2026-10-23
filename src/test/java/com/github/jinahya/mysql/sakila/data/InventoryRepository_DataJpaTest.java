package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Inventory;
import com.github.jinahya.mysql.sakila.persistence.Inventory_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link InventoryRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. A copy needs a store, and a store its manager, who needs the store,
 * so neither can be persisted into the empty H2 (see {@link _Repository_DataJpaTest_Fixtures}): the exercises run
 * against an empty {@code inventory} table here, and assert an empty list, stream or page, which still proves their
 * JPQL, their count queries and their sorts run, and their argument checks. The native-only
 * {@link InventoryRepository#selectLongestIdleTimes()} is MySQL's own SQL, so it has no test here. The integration
 * test checks them all against the sample data.
 *
 * @see InventoryRepository_SpringBootIT
 */
class InventoryRepository_DataJpaTest
        extends _Repository_DataJpaTest<InventoryRepository, Inventory, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Inventory_.inventoryId} is filled in only
     * once the context is up, after this class is loaded.
     */
    private static final Sort BY_INVENTORY_ID = Sort.by(Sort.Direction.ASC, Inventory_.INVENTORY_ID);

    /**
     * Creates a new instance.
     */
    InventoryRepository_DataJpaTest() {
        super(InventoryRepository.class, Inventory.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link InventoryRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * No copy can be persisted into the H2, so no id is known; the test proves the find runs against the table.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByStore()} ({@code SAKILA-INVENTORY-B-01}).
     */
    @Nested
    class SelectCopyCountsByStore_Test {

        /**
         * Asserts that, with no copy, the grouped select, ordered by store id in the query, runs and selects an empty
         * list.
         */
        @Test
        void _Empty_NoCopy() {
            final var list = repositoryInstance().selectCopyCountsByStore();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link InventoryRepository#findAllByFilmIdOrderByInventoryIdAsc(Integer)} ({@code SAKILA-INVENTORY-B-02}).
     */
    @Nested
    class FindAllByFilmIdOrderByInventoryIdAsc_Test {

        /**
         * Saves a film, and asserts that, with no copy, its id streams nothing.
         */
        @Test
        void _Empty_NoCopy() {
            final var film = fixtures().film();
            try (var found = repositoryInstance().findAllByFilmIdOrderByInventoryIdAsc(film.getFilmId())) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException} whether or not the film has a copy.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByFilmIdOrderByInventoryIdAsc(1)) {
                    found.forEach(i -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} film id is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings({"DataFlowIssue", "resource"})
        @Test
        void _IllegalArgumentException_NullFilmId() {
            assertThatThrownBy(() -> repositoryInstance().findAllByFilmIdOrderByInventoryIdAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByFilm(Pageable)} ({@code SAKILA-INVENTORY-I-01}).
     */
    @Nested
    class SelectCopyCountsByFilm_Test {

        private static final Sort BY_COPIES_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "copies").and(Sort.by("f.filmId"));

        /**
         * Saves a film, and asserts that the page is empty, with no elements in all: a film with no copy is not
         * selected.
         */
        @Test
        void _Empty_NoCopy() {
            fixtures().film();
            final var page = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(0, 5, BY_COPIES_DESC_THEN_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page, sorted the exercise's way, is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(1, 5, BY_COPIES_DESC_THEN_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCopyCountsByFilm(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link InventoryRepository#listNeverRented(Pageable)} ({@code SAKILA-INVENTORY-I-02}).
     */
    @Nested
    class ListNeverRented_Test {

        /**
         * Asserts that, with no copy, the {@code IS EMPTY} criteria query, sorted by id, lists an empty page, with no
         * copies in all.
         */
        @Test
        void _Empty_NoCopy() {
            final var page = repositoryInstance().listNeverRented(PageRequest.of(0, 5, BY_INVENTORY_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().listNeverRented(PageRequest.of(1, 5, BY_INVENTORY_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before the method's own check, or any query, runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listNeverRented(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link InventoryRepository#selectCopyCountsOfFilmsEveryCopyRented(Pageable)}
     * ({@code SAKILA-INVENTORY-A-02}).
     */
    @Nested
    class SelectCopyCountsOfFilmsEveryCopyRented_Test {

        private static final Sort BY_FILM_ID = Sort.by("f.filmId");

        /**
         * Saves a film, and asserts that the page is empty, with no films in all: a film with no copy has none that
         * was rented, but is not selected either.
         */
        @Test
        void _Empty_NoCopy() {
            fixtures().film();
            final var page = repositoryInstance().selectCopyCountsOfFilmsEveryCopyRented(
                    PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectCopyCountsOfFilmsEveryCopyRented(
                    PageRequest.of(1, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCopyCountsOfFilmsEveryCopyRented(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
