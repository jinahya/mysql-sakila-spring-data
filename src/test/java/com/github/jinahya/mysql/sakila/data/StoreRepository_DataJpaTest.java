package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Store;
import com.github.jinahya.mysql.sakila.persistence.Store_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link StoreRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No store can be persisted into the H2: a store names its manager, a
 * staff member, who works at a store, both {@code NOT NULL}, so neither of the two can be saved first (see
 * {@link _Repository_DataJpaTest_Fixtures}). Each test here runs its query against an empty {@code store} table, and
 * asserts an empty list or page; that still proves that the derived query, the JPQL, or the criteria query, is valid
 * and runs on the H2, with the sort the exercise asks for.
 *
 * @see StoreRepository_SpringBootIT
 */
class StoreRepository_DataJpaTest
        extends _Repository_DataJpaTest<StoreRepository, Store, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Store_.storeId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_STORE_ID = Sort.by(Sort.Direction.ASC, Store_.STORE_ID);

    /**
     * Creates a new instance.
     */
    StoreRepository_DataJpaTest() {
        super(StoreRepository.class, Store.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link StoreRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The table is empty, and stays so, so any id misses.
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
     * Tests {@link StoreRepository#findAllByOrderByStoreIdAsc()} ({@code SAKILA-STORE-B-01}).
     */
    @Nested
    class FindAllByOrderByStoreIdAsc_Test {

        /**
         * Asserts that the derived query, ordered by id as the exercise orders, runs on the empty table and finds an
         * empty list.
         */
        @Test
        void _Empty_NoStores() {
            final var found = repositoryInstance().findAllByOrderByStoreIdAsc();
            assertThat(found).isEmpty();
        }
    }

    /**
     * Tests {@link StoreRepository#listWithManagerAndCity(Pageable)} ({@code SAKILA-STORE-I-01}).
     */
    @Nested
    class ListWithManagerAndCity_Test {

        /**
         * Asserts that the criteria query with its three fetches, sorted by id as the exercise sorts, runs on the
         * empty tables and lists an empty page, with no stores in all.
         * <p>
         * The page runs two queries, the select with the fetches and the count without them; both have to be valid.
         */
        @Test
        void _Empty_NoStores() {
            final var page = repositoryInstance().listWithManagerAndCity(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before the method builds its query.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument even of a {@code default} method, and
         * throws {@link IllegalArgumentException} before the method's own {@code requireNonNull} is reached.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listWithManagerAndCity(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link StoreRepository#selectCounts(Pageable)} ({@code SAKILA-STORE-I-02}).
     */
    @Nested
    class SelectCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("st.storeId");

        /**
         * Asserts that the select with its three scalar subqueries, sorted by the id as the exercise sorts, runs on
         * the empty tables and selects an empty page, with no stores in all.
         */
        @Test
        void _Empty_NoStores() {
            final var page = repositoryInstance().selectCounts(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that the select sorted by one of the subquery columns, which is an alias of the select and not a
         * path of the entity, runs on the empty tables too.
         */
        @Test
        void _Empty_SortedByCustomers() {
            final var page = repositoryInstance().selectCounts(
                    PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "customers")));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument, and throws
         * {@link IllegalArgumentException}.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link StoreRepository#selectFilmCounts(Pageable)} ({@code SAKILA-STORE-A-01}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("st.storeId");

        /**
         * Asserts that the joined and grouped select, with its {@code CASE} on a correlated {@code NOT EXISTS}, sorted
         * by the id as the exercise sorts, runs on the empty tables and selects an empty page, with no stores in all.
         */
        @Test
        void _Empty_NoStores() {
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument, and throws
         * {@link IllegalArgumentException}.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectFilmCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link StoreRepository#selectMonthlyRentalShares(Pageable)} ({@code SAKILA-STORE-A-02}).
     */
    @Nested
    class SelectMonthlyRentalShares_Test {

        private static final Sort BY_MONTH_THEN_STORE_ID = Sort.by("rentalYear", "rentalMonth", "st.storeId");

        /**
         * Asserts that the grouped select, with its window function over the groups, and its count query, sorted by
         * the month and then the store as the exercise sorts, run on the empty tables and select an empty page, with
         * no months in all.
         */
        @Test
        void _Empty_NoRentals() {
            final var page = repositoryInstance().selectMonthlyRentalShares(
                    PageRequest.of(0, 5, BY_MONTH_THEN_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument, and throws
         * {@link IllegalArgumentException}.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectMonthlyRentalShares(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
