package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory;
import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link SalesByFilmCategoryRepository} in a JPA slice, over an embedded H2, through
 * {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. The slice generates {@code sales_by_film_category} as a table, from
 * the entity, but its {@code total_sales} is read-only ({@code insertable = false}), so no row can be persisted into
 * it, and the fixtures do not try; nor can a payment, which needs a store. Each test here runs its query against the
 * empty table, and asserts an empty list or page, or a null sum: that proves the derived query, the JPQL, or the
 * criteria query, is valid for Hibernate and runs on H2, which is what a slice test of a view can prove. What the
 * queries return on the sample data is for {@link SalesByFilmCategoryRepository_SpringBootIT}.
 *
 * @see SalesByFilmCategoryRepository_SpringBootIT
 */
class SalesByFilmCategoryRepository_DataJpaTest
        extends _Repository_DataJpaTest<SalesByFilmCategoryRepository, SalesByFilmCategory, String> {

    /**
     * A sort by {@code total_sales} descending, the most sold first, as the exercise orders; by the attribute's name,
     * since the metamodel's {@code SalesByFilmCategory_.totalSales} is filled in only once the context is up, after
     * this class is loaded.
     */
    private static final Sort BY_TOTAL_SALES_DESC = Sort.by(Sort.Direction.DESC, SalesByFilmCategory_.TOTAL_SALES);

    /**
     * Creates a new instance.
     */
    SalesByFilmCategoryRepository_DataJpaTest() {
        super(SalesByFilmCategoryRepository.class, SalesByFilmCategory.class, String.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link SalesByFilmCategoryRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random category name returns an empty optional.
         * <p>
         * The id of the view is the category's name, a string; the table is empty, so any name misses.
         */
        @Test
        void _Empty_Unknown() {
            final var found = repositoryInstance().findById(randomName());
            assertThat(found).isEmpty();
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link SalesByFilmCategoryRepository#listAll(Pageable)} ({@code SAKILA-SALES_BY_FILM_CATEGORY-B-01}).
     */
    @Nested
    class ListAll_Test {

        /**
         * Asserts that the empty table lists an empty page of three, by {@code total_sales} descending as the
         * exercise sorts, with no elements in all, and so does an unpaged request.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().listAll(PageRequest.of(0, 3, BY_TOTAL_SALES_DESC));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            final var all = repositoryInstance().listAll(Pageable.unpaged(BY_TOTAL_SALES_DESC));
            assertThat(all).isEmpty();
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
            assertThatThrownBy(() -> repositoryInstance().listAll(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#findAllByTotalSalesBetweenOrderByCategoryAsc(BigDecimal, BigDecimal)}
     * ({@code SAKILA-SALES_BY_FILM_CATEGORY-B-02}).
     */
    @Nested
    class FindAllByTotalSalesBetweenOrderByCategoryAsc_Test {

        /**
         * Asserts that the derived query, with the exercise's bounds and ordered by the category as the exercise
         * orders, runs on the empty table and finds an empty list.
         */
        @Test
        void _Empty_Empty() {
            final var found = repositoryInstance().findAllByTotalSalesBetweenOrderByCategoryAsc(
                    BigDecimal.valueOf(4000L), BigDecimal.valueOf(4500L));
            assertThat(found).isEmpty();
        }

        /**
         * Asserts that a {@code null} bound, either one, is rejected before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments, and throws
         * {@link IllegalArgumentException}.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullBound() {
            assertThatThrownBy(() -> repositoryInstance().findAllByTotalSalesBetweenOrderByCategoryAsc(
                    null, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repositoryInstance().findAllByTotalSalesBetweenOrderByCategoryAsc(
                    BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectSalesTotals()} ({@code SAKILA-SALES_BY_FILM_CATEGORY-I-01}).
     */
    @Nested
    class SelectSalesTotals_Test {

        /**
         * Asserts that the select has its one row even over the empty view, and that both sums are {@code null}: the
         * sum of no row is {@code null}, not zero, in the view and in the {@code payment} table alike, and no payment
         * can be saved into the slice.
         * <p>
         * The second sum is a subquery in the select, on {@code Payment}; the test proves that Hibernate accepts it
         * there and that H2 runs it.
         */
        @Test
        void _Null_Empty() {
            final var totals = repositoryInstance().selectSalesTotals();
            assertThat(totals).isNotNull();
            assertThat(totals.getByCategory()).isNull();
            assertThat(totals.getAllPayments()).isNull();
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectSalesShares(Pageable)}
     * ({@code SAKILA-SALES_BY_FILM_CATEGORY-I-02}).
     */
    @Nested
    class SelectSalesShares_Test {

        private static final Sort BY_TOTAL_SALES_DESC_THEN_CATEGORY =
                Sort.by(Sort.Direction.DESC, "totalSales").and(Sort.by("category"));

        /**
         * Asserts that the select, with its scalar subquery for the sum of all sales, and its count query, sorted by
         * the sales descending and then the category as the exercise sorts, run on the empty table and select an
         * empty page, with no categories in all.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().selectSalesShares(
                    PageRequest.of(0, 5, BY_TOTAL_SALES_DESC_THEN_CATEGORY));
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
            assertThatThrownBy(() -> repositoryInstance().selectSalesShares(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectRunningTotals(Pageable)}
     * ({@code SAKILA-SALES_BY_FILM_CATEGORY-A-01}).
     */
    @Nested
    class SelectRunningTotals_Test {

        private static final Sort BY_TOTAL_SALES_DESC_THEN_CATEGORY =
                Sort.by(Sort.Direction.DESC, "totalSales").and(Sort.by("category"));

        /**
         * Asserts that the self-joined and grouped select, with its {@code CASE} sums, and its count query, sorted by
         * the sales descending and then the category as the exercise sorts, run on the empty table and select an
         * empty page, with no categories in all.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().selectRunningTotals(
                    Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY));
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
            assertThatThrownBy(() -> repositoryInstance().selectRunningTotals(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectGaps(Pageable)} ({@code SAKILA-SALES_BY_FILM_CATEGORY-A-02}).
     */
    @Nested
    class SelectGaps_Test {

        private static final Sort BY_GAP_DESC_THEN_CATEGORY =
                Sort.by(Sort.Direction.DESC, "gap").and(Sort.by("category"));

        /**
         * Asserts that the self-joined select, with its correlated {@code NOT EXISTS}, and its count query, sorted by
         * the gap descending and then the category as the exercise sorts, run on the empty table and select an empty
         * page, with no pairs in all.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().selectGaps(PageRequest.of(0, 3, BY_GAP_DESC_THEN_CATEGORY));
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
            assertThatThrownBy(() -> repositoryInstance().selectGaps(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
