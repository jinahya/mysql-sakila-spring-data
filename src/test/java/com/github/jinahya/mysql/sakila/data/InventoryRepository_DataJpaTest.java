package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Inventory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link InventoryRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. A copy needs a store, and a store its manager, who needs the store,
 * so neither can be persisted into the empty H2 (see {@link _Repository_DataJpaTest_Fixtures}): the exercises run
 * against an empty {@code inventory} table here, and assert an empty page or zero, which still proves their JPQL,
 * their count queries and their sorts run. The integration test checks them against the sample data.
 *
 * @see InventoryRepository_SpringBootIT
 */
class InventoryRepository_DataJpaTest
        extends _Repository_DataJpaTest<InventoryRepository, Inventory, Integer> {

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
     * Tests {@link InventoryRepository#selectCopyCountsByStore(Pageable)} ({@code SAKILA-INVENTORY-B-01}).
     */
    @Nested
    class SelectCopyCountsByStore_Test {

        private static final Sort BY_STORE_ID = Sort.by("storeId");

        /**
         * Asserts that, with no copy, the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoCopy() {
            final var page = repositoryInstance().selectCopyCountsByStore(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByFilm(Pageable)} ({@code SAKILA-INVENTORY-I-01}).
     */
    @Nested
    class SelectCopyCountsByFilm_Test {

        private static final Sort BY_COPIES_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "copies").and(Sort.by("filmId"));

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
    }

    /**
     * Tests {@link InventoryRepository#countNeverRented()} ({@code SAKILA-INVENTORY-I-02}).
     */
    @Nested
    class CountNeverRented_Test {

        /**
         * Asserts that, with no copy, the count is zero.
         */
        @Test
        void _Zero_NoCopy() {
            assertThat(repositoryInstance().countNeverRented()).isZero();
        }
    }
}
