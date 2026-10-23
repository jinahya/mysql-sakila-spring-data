package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.InventoryRepository.FilmCopyCount;
import com.github.jinahya.mysql.sakila.data.InventoryRepository.StoreCopyCount;
import com.github.jinahya.mysql.sakila.persistence.Inventory;
import com.github.jinahya.mysql.sakila.persistence.Inventory_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link InventoryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few copies of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * counts of the copies taken another way. The base's own tests, such as its {@code findById} round trips, run here
 * too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see InventoryRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryRepository_SpringBootIT
        extends _Repository_SpringBootIT<InventoryRepository, Inventory, Integer> {

    private static List<Inventory> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Inventory_.inventoryId} is filled in only
     * once the context is up, after this class is loaded.
     */
    private static final Sort BY_INVENTORY_ID = Sort.by(Sort.Direction.ASC, Inventory_.INVENTORY_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    InventoryRepository_SpringBootIT() {
        super(InventoryRepository.class, Inventory.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five copies, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The copies are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_INVENTORY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Inventory> inventories() {
        return entities.stream();
    }

    /**
     * Counts the copies of the store of the specified id, through a {@link Specification} on the read-only
     * {@link Inventory_#storeId}.
     *
     * @param storeId the id of the store.
     * @return the number of copies of the store.
     */
    private long countByStoreId(final Integer storeId) {
        final Specification<Inventory> specification = (r, q, b) -> b.equal(r.get(Inventory_.storeId), storeId);
        return repositoryInstance().count(specification);
    }

    /**
     * Counts the copies of the film of the specified id, through a {@link Specification} on the read-only
     * {@link Inventory_#filmId}.
     *
     * @param filmId the id of the film.
     * @return the number of copies of the film.
     */
    private long countByFilmId(final Integer filmId) {
        final Specification<Inventory> specification = (r, q, b) -> b.equal(r.get(Inventory_.filmId), filmId);
        return repositoryInstance().count(specification);
    }

    /**
     * Asserts that the specified page is consistent with itself: no more content than its size, and at least as many
     * elements in all as in its content.
     *
     * @param page the page.
     * @param <T>  the type of the elements.
     */
    private static <T> void assertPage(final Page<T> page) {
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(page.getSize());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(page.getNumberOfElements());
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByStore(Pageable)} ({@code SAKILA-INVENTORY-B-01}).
     */
    @Nested
    class SelectCopyCountsByStore_Test {

        private static final Sort BY_STORE_ID = Sort.by("storeId");

        private static Stream<Inventory> inventories() {
            return InventoryRepository_SpringBootIT.inventories();
        }

        /**
         * Asserts that the stores, by store id as the exercise sorts, are ordered so, that each count is the number of
         * copies of the store, and that the counts add up to all copies.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectCopyCountsByStore(PageRequest.of(0, 5, BY_STORE_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(StoreCopyCount::getStoreId))
                    .allSatisfy(c -> {
                        assertThat(c.getCopies()).as("copies of store %d", c.getStoreId())
                                .isPositive()
                                .isEqualTo(countByStoreId(c.getStoreId()));
                    });
            if (!page.hasNext()) {
                final var sum = page.getContent().stream().mapToLong(StoreCopyCount::getCopies).sum();
                assertThat(sum).isEqualTo(repositoryInstance().count());
            }
        }

        /**
         * Asserts that the store of a selected copy is among the stores selected.
         *
         * @param inventory one of the copies selected in {@link InventoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("inventories")
        @ParameterizedTest
        void __ContainsStoreOfFixture(final Inventory inventory) {
            final var all = repositoryInstance().selectCopyCountsByStore(Pageable.unpaged(BY_STORE_ID));
            assertThat(all.getContent())
                    .extracting(StoreCopyCount::getStoreId)
                    .contains(inventory.getStoreId());
        }
    }

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByFilm(Pageable)} ({@code SAKILA-INVENTORY-I-01}).
     */
    @Nested
    class SelectCopyCountsByFilm_Test {

        private static final Sort BY_COPIES_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "copies").and(Sort.by("filmId"));

        private static Stream<Inventory> inventories() {
            return InventoryRepository_SpringBootIT.inventories();
        }

        /**
         * Asserts that a page of the given size, the most copied first as the exercise sorts, is ordered so, that
         * each title is the film's, read through {@link FilmRepository}, and that each count is the number of copies
         * of the film.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(0, size, BY_COPIES_DESC_THEN_FILM_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparingLong(FilmCopyCount::getCopies).reversed()
                                                 .thenComparing(FilmCopyCount::getFilmId))
                    .allSatisfy(c -> {
                        final var film = filmRepository.findById(c.getFilmId()).orElseThrow();
                        assertThat(film.getTitle()).isEqualTo(c.getTitle());
                        assertThat(c.getCopies()).as("copies of film %d", c.getFilmId())
                                .isPositive()
                                .isEqualTo(countByFilmId(c.getFilmId()));
                    });
        }

        /**
         * Asserts that the film of a selected copy is among the films selected.
         *
         * @param inventory one of the copies selected in {@link InventoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("inventories")
        @ParameterizedTest
        void __ContainsFilmOfFixture(final Inventory inventory) {
            final var all = repositoryInstance().selectCopyCountsByFilm(Pageable.unpaged(BY_COPIES_DESC_THEN_FILM_ID));
            assertThat(all.getContent())
                    .extracting(FilmCopyCount::getFilmId)
                    .contains(inventory.getFilmId());
        }
    }

    /**
     * Tests {@link InventoryRepository#countNeverRented()} ({@code SAKILA-INVENTORY-I-02}).
     */
    @Nested
    class CountNeverRented_Test {

        /**
         * Asserts that the count is the number of all copies less the number of copies with a rental, counted by the
         * complementary {@link Specification}, {@code IS NOT EMPTY} on the copy's rentals.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countNeverRented();
            final Specification<Inventory> rented = (r, q, b) -> b.isNotEmpty(r.get(Inventory_.rentals));
            final var all = repositoryInstance().count();
            assertThat(count)
                    .isNotNegative()
                    .isLessThanOrEqualTo(all)
                    .isEqualTo(all - repositoryInstance().count(rented));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
