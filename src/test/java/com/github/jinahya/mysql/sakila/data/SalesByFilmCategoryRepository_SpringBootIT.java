package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory;
import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory_;
import jakarta.persistence.Tuple;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link SalesByFilmCategoryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rows of the
 * view, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against the
 * {@code payment} table the view sums, read through {@link PaymentRepository}. The base's own tests, such as its
 * {@code findById} round trips, run here too; the id of this view is the category's name.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see SalesByFilmCategoryRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesByFilmCategoryRepository_SpringBootIT
        extends _Repository_SpringBootIT<SalesByFilmCategoryRepository, SalesByFilmCategory, String> {

    private static List<SalesByFilmCategory> entities;

    /**
     * A sort by {@code total_sales} descending, the most sold first, as the exercise orders, then by the category's
     * name so that the order is the same every time; by the attributes' names, since the metamodel's
     * {@code SalesByFilmCategory_.totalSales} is filled in only once the context is up, after this class is loaded.
     */
    private static final Sort BY_TOTAL_SALES_DESC_THEN_CATEGORY =
            Sort.by(Sort.Direction.DESC, SalesByFilmCategory_.TOTAL_SALES)
                    .and(Sort.by(Sort.Direction.ASC, SalesByFilmCategory_.CATEGORY));

    /**
     * The bounds of {@code SAKILA-SALES_BY_FILM_CATEGORY-B-02}.
     */
    private static final BigDecimal LOW = BigDecimal.valueOf(4000L);

    private static final BigDecimal HIGH = BigDecimal.valueOf(4500L);

    /**
     * A sort by the category's name, the id of the view.
     */
    private static final Sort BY_CATEGORY = Sort.by(Sort.Direction.ASC, SalesByFilmCategory_.CATEGORY);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    SalesByFilmCategoryRepository_SpringBootIT() {
        super(SalesByFilmCategoryRepository.class, SalesByFilmCategory.class, String.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rows of the view, by the category's name, its id, as the fixture for the parameterized
     * tests.
     * <p>
     * Runs once per class, which {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the
     * autowired repository is already there; the result goes into a static field so that the static method sources
     * of the nested classes can reach it. The rows are loaded outside any transaction, so they are detached.
     * <p>
     * An empty view aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_CATEGORY)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<SalesByFilmCategory> salesByFilmCategories() {
        return entities.stream();
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
     * Tests {@link SalesByFilmCategoryRepository#listAll(Pageable)} ({@code SAKILA-SALES_BY_FILM_CATEGORY-B-01}).
     */
    @Nested
    class ListAll_Test {

        private static Stream<SalesByFilmCategory> salesByFilmCategories() {
            return SalesByFilmCategoryRepository_SpringBootIT.salesByFilmCategories();
        }

        /**
         * Asserts that a page of the given size, the most sold first as the exercise sorts, is ordered so, and that
         * it holds the first rows of the whole view in that order; the exercise's three are the page of three.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 3, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().listAll(PageRequest.of(0, size, BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(SalesByFilmCategory::getTotalSales).reversed()
                                                 .thenComparing(SalesByFilmCategory::getCategory));
            final var all = repositoryInstance().listAll(Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            assertThat(all.getContent()).startsWith(page.getContent().toArray(new SalesByFilmCategory[0]));
        }

        /**
         * Asserts that every row lists a selected row, with the total sales the row has.
         *
         * @param salesByFilmCategory one of the rows selected in
         *                            {@link SalesByFilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("salesByFilmCategories")
        @ParameterizedTest
        void __Selected(final SalesByFilmCategory salesByFilmCategory) {
            final var all = repositoryInstance().listAll(Pageable.unpaged(BY_CATEGORY));
            assertThat(all.getContent())
                    .contains(salesByFilmCategory)
                    .filteredOn(s -> s.getCategory().equals(salesByFilmCategory.getCategory()))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.getTotalSales()).isEqualByComparingTo(salesByFilmCategory.getTotalSales());
                    });
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#findAllByTotalSalesBetweenOrderByCategoryAsc(BigDecimal, BigDecimal)}
     * ({@code SAKILA-SALES_BY_FILM_CATEGORY-B-02}).
     */
    @Nested
    class FindAllByTotalSalesBetweenOrderByCategoryAsc_Test {

        private static Stream<SalesByFilmCategory> salesByFilmCategories() {
            return SalesByFilmCategoryRepository_SpringBootIT.salesByFilmCategories();
        }

        /**
         * Asserts that the categories which sold between {@code 4000} and {@code 4500}, by name as the exercise
         * orders, are the rows the exercise shows, on the sample data.
         */
        @Test
        void __Expected() {
            final var found = repositoryInstance().findAllByTotalSalesBetweenOrderByCategoryAsc(LOW, HIGH);
            assertThat(found)
                    .extracting(SalesByFilmCategory::getCategory, s -> s.getTotalSales().doubleValue())
                    .containsExactly(
                            tuple("Action", 4375.85d),
                            tuple("Comedy", 4383.58d),
                            tuple("Documentary", 4217.52d),
                            tuple("Family", 4226.07d),
                            tuple("Foreign", 4270.67d),
                            tuple("Games", 4281.33d),
                            tuple("New", 4351.62d)
                    );
        }

        /**
         * Asserts that a selected row is found exactly when its sales are between the bounds, both included, and that
         * every row found is so, by name.
         *
         * @param salesByFilmCategory one of the rows selected in
         *                            {@link SalesByFilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("salesByFilmCategories")
        @ParameterizedTest
        void __(final SalesByFilmCategory salesByFilmCategory) {
            final var found = repositoryInstance().findAllByTotalSalesBetweenOrderByCategoryAsc(LOW, HIGH);
            assertThat(found)
                    .isSortedAccordingTo(Comparator.comparing(SalesByFilmCategory::getCategory))
                    .allSatisfy(s -> assertThat(s.getTotalSales()).isBetween(LOW, HIGH));
            final var totalSales = salesByFilmCategory.getTotalSales();
            if (totalSales.compareTo(LOW) >= 0 && totalSales.compareTo(HIGH) <= 0) {
                assertThat(found).contains(salesByFilmCategory);
            } else {
                assertThat(found).doesNotContain(salesByFilmCategory);
            }
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectSalesTotals()} ({@code SAKILA-SALES_BY_FILM_CATEGORY-I-01}).
     */
    @Nested
    class SelectSalesTotals_Test {

        private static Stream<SalesByFilmCategory> salesByFilmCategories() {
            return SalesByFilmCategoryRepository_SpringBootIT.salesByFilmCategories();
        }

        /**
         * Asserts that the view's sales add up to every payment, as the exercise checks, that the sum by category is
         * the sum of the {@code total_sales} of every row the view's own repository lists, and that a selected row's
         * sales are part of it; and that the sum of all payments is positive, since the {@code payment} table, read
         * through {@link PaymentRepository}, is not empty.
         *
         * @param salesByFilmCategory one of the rows selected in
         *                            {@link SalesByFilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("salesByFilmCategories")
        @ParameterizedTest
        void __(final SalesByFilmCategory salesByFilmCategory) {
            final var totals = repositoryInstance().selectSalesTotals();
            final var byCategory = totals.get("byCategory", BigDecimal.class);
            assertThat(byCategory).isNotNull().isPositive();
            assertThat(totals.get("allPayments", BigDecimal.class)).isNotNull().isEqualByComparingTo(byCategory);
            final var summed = repositoryInstance().findAll(Pageable.unpaged()).stream()
                    .map(SalesByFilmCategory::getTotalSales)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(byCategory).isEqualByComparingTo(summed);
            assertThat(salesByFilmCategory.getTotalSales()).isLessThanOrEqualTo(byCategory);
            assertThat(paymentRepository.count()).isPositive();
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectSalesShares(Pageable)}
     * ({@code SAKILA-SALES_BY_FILM_CATEGORY-I-02}).
     */
    @Nested
    class SelectSalesShares_Test {

        private static final Sort BY_TOTAL_SALES_DESC_THEN_CATEGORY =
                Sort.by(Sort.Direction.DESC, "s.totalSales").and(Sort.by("s.category"));

        private static Stream<SalesByFilmCategory> salesByFilmCategories() {
            return SalesByFilmCategoryRepository_SpringBootIT.salesByFilmCategories();
        }

        /**
         * Asserts that the first page of five, the most sold first, then by name, as the exercise sorts, holds the
         * rows the exercise shows, on the sample data, and that the count query counts every row of the view.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectSalesShares(
                    PageRequest.of(0, 5, BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            assertThat(page.getContent())
                    .extracting(column("salesByFilmCategory", SalesByFilmCategory.class,
                                       SalesByFilmCategory::getCategory),
                                c -> c.get("salesByFilmCategory", SalesByFilmCategory.class)
                                        .getTotalSales().doubleValue(),
                                c -> c.get("percent", BigDecimal.class).doubleValue())
                    .containsExactly(
                            tuple("Sports", 5314.21d, 7.88d),
                            tuple("Sci-Fi", 4756.98d, 7.06d),
                            tuple("Animation", 4656.30d, 6.91d),
                            tuple("Drama", 4587.39d, 6.81d),
                            tuple("Comedy", 4383.58d, 6.50d)
                    );
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the shares of all rows add up to a hundred, give or take the rounding, and that a selected row
         * is among them, the row itself, with its own sales.
         *
         * @param salesByFilmCategory one of the rows selected in
         *                            {@link SalesByFilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("salesByFilmCategories")
        @ParameterizedTest
        void __(final SalesByFilmCategory salesByFilmCategory) {
            final var all = repositoryInstance().selectSalesShares(Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            final var percent = all.stream().map(column("percent", BigDecimal.class)).reduce(BigDecimal.ZERO,
                                                                                         BigDecimal::add);
            assertThat(percent.doubleValue()).isBetween(99.9d, 100.1d);
            assertThat(all.getContent())
                    .filteredOn(c -> c.get("salesByFilmCategory", SalesByFilmCategory.class)
                            .equals(salesByFilmCategory))
                    .singleElement()
                    .satisfies(c -> {
                        assertThat(c.get("salesByFilmCategory", SalesByFilmCategory.class).getTotalSales())
                                .isEqualByComparingTo(salesByFilmCategory.getTotalSales());
                    });
        }

        /**
         * Asserts that a page of the given size, the most sold first, is consistent, and ordered so.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __Paged(final int size) {
            final var page = repositoryInstance().selectSalesShares(
                    PageRequest.of(1, size, BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            assertPage(page);
            assertThat(page.getContent())
                    .hasSize(size)
                    .isSortedAccordingTo(
                            Comparator.comparing(column("salesByFilmCategory", SalesByFilmCategory.class,
                                                        SalesByFilmCategory::getTotalSales))
                                    .reversed()
                                    .thenComparing(column("salesByFilmCategory", SalesByFilmCategory.class,
                                                          SalesByFilmCategory::getCategory)));
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectRunningTotals(Pageable)}
     * ({@code SAKILA-SALES_BY_FILM_CATEGORY-A-01}).
     */
    @Nested
    class SelectRunningTotals_Test {

        private static final Sort BY_TOTAL_SALES_DESC_THEN_CATEGORY =
                Sort.by(Sort.Direction.DESC, "s.totalSales").and(Sort.by("s.category"));

        private static Stream<SalesByFilmCategory> salesByFilmCategories() {
            return SalesByFilmCategoryRepository_SpringBootIT.salesByFilmCategories();
        }

        /**
         * Asserts that all the rows, the most sold first, then by name, as the exercise sorts, start and end with the
         * rows the exercise shows, on the sample data, sixteen in all.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectRunningTotals(
                    Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            assertThat(page.getContent())
                    .hasSize(16)
                    .extracting(column("salesByFilmCategory", SalesByFilmCategory.class,
                                       SalesByFilmCategory::getCategory),
                                c -> c.get("salesByFilmCategory", SalesByFilmCategory.class)
                                        .getTotalSales().doubleValue(),
                                c -> c.get("runningTotal", BigDecimal.class).doubleValue(),
                                c -> c.get("runningPercent", BigDecimal.class).doubleValue())
                    .startsWith(
                            tuple("Sports", 5314.21d, 5314.21d, 7.88d),
                            tuple("Sci-Fi", 4756.98d, 10071.19d, 14.94d),
                            tuple("Animation", 4656.30d, 14727.49d, 21.85d)
                    )
                    .endsWith(
                            tuple("Travel", 3549.64d, 63988.84d, 94.93d),
                            tuple("Music", 3417.72d, 67406.56d, 100.00d)
                    );
        }

        /**
         * Asserts that, in the exercise's order, each running total is the one before it plus the row's own sales, and
         * that the last is the sum of all sales, a hundred percent; and that a selected row is among them.
         *
         * @param salesByFilmCategory one of the rows selected in
         *                            {@link SalesByFilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("salesByFilmCategories")
        @ParameterizedTest
        void __(final SalesByFilmCategory salesByFilmCategory) {
            final var all = repositoryInstance().selectRunningTotals(
                    Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY)).getContent();
            var previous = BigDecimal.ZERO;
            for (final var c : all) {
                final var runningTotal = c.get("runningTotal", BigDecimal.class);
                final var totalSales = c.get("salesByFilmCategory", SalesByFilmCategory.class).getTotalSales();
                assertThat(runningTotal).isEqualByComparingTo(previous.add(totalSales));
                previous = runningTotal;
            }
            assertThat(all.getLast().get("runningPercent", BigDecimal.class))
                    .isEqualByComparingTo(BigDecimal.valueOf(100L));
            assertThat(all).anySatisfy(
                    c -> assertThat(c.get("salesByFilmCategory", SalesByFilmCategory.class))
                            .isEqualTo(salesByFilmCategory));
        }

        /**
         * Asserts that the pages of the given size walk the same rows, with the same running totals, as the unpaged
         * page, and that the count query counts every row of the view.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {5, 7})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().selectRunningTotals(
                    Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY)).getContent();
            var page = repositoryInstance().selectRunningTotals(PageRequest.of(0, size,
                                                                               BY_TOTAL_SALES_DESC_THEN_CATEGORY));
            assertThat(page.getTotalElements()).isEqualTo(all.size());
            final var walked = new ArrayList<Tuple>();
            while (true) {
                assertPage(page);
                walked.addAll(page.getContent());
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().selectRunningTotals(page.nextPageable());
            }
            assertThat(walked)
                    .extracting(column("salesByFilmCategory", SalesByFilmCategory.class),
                                column("runningTotal", BigDecimal.class))
                    .containsExactlyElementsOf(
                            all.stream()
                                    .map(c -> tuple(c.get("salesByFilmCategory", SalesByFilmCategory.class),
                                                    c.get("runningTotal", BigDecimal.class)))
                                    .toList());
        }
    }

    /**
     * Tests {@link SalesByFilmCategoryRepository#selectGaps(Pageable)} ({@code SAKILA-SALES_BY_FILM_CATEGORY-A-02}).
     */
    @Nested
    class SelectGaps_Test {

        private static final Sort BY_GAP_DESC_THEN_CATEGORY =
                Sort.by(Sort.Direction.DESC, "gap").and(Sort.by("s.category"));

        private static final Sort BY_S_TOTAL_SALES_DESC_THEN_S_CATEGORY =
                Sort.by(Sort.Direction.DESC, "s.totalSales").and(Sort.by("s.category"));

        private static Stream<SalesByFilmCategory> salesByFilmCategories() {
            return SalesByFilmCategoryRepository_SpringBootIT.salesByFilmCategories();
        }

        /**
         * Asserts that the first page of three, the largest gap first, then by name, as the exercise sorts, holds the
         * rows the exercise shows, on the sample data, and that the count query counts every row of the view but the
         * last, which has no next category.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectGaps(PageRequest.of(0, 3, BY_GAP_DESC_THEN_CATEGORY));
            assertThat(page.getContent())
                    .extracting(column("salesByFilmCategory", SalesByFilmCategory.class,
                                       SalesByFilmCategory::getCategory),
                                c -> c.get("salesByFilmCategory", SalesByFilmCategory.class)
                                        .getTotalSales().doubleValue(),
                                column("nextSalesByFilmCategory", SalesByFilmCategory.class,
                                       SalesByFilmCategory::getCategory),
                                c -> c.get("gap", BigDecimal.class).doubleValue())
                    .containsExactly(
                            tuple("Sports", 5314.21d, "Sci-Fi", 557.23d),
                            tuple("Documentary", 4217.52d, "Horror", 494.98d),
                            tuple("Drama", 4587.39d, "Comedy", 203.81d)
                    );
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count() - 1L);
        }

        /**
         * Asserts that the pairs, in the exercise's order of the categories, chain each category to the one next
         * down, each row compared with the one of the view itself, with the difference of their sales as the gap;
         * and that a selected row is the first of exactly one pair, unless it is the last.
         *
         * @param salesByFilmCategory one of the rows selected in
         *                            {@link SalesByFilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("salesByFilmCategories")
        @ParameterizedTest
        void __(final SalesByFilmCategory salesByFilmCategory) {
            final var ordered = repositoryInstance().listAll(Pageable.unpaged(BY_TOTAL_SALES_DESC_THEN_CATEGORY))
                    .getContent();
            final var gaps = repositoryInstance().selectGaps(Pageable.unpaged(BY_S_TOTAL_SALES_DESC_THEN_S_CATEGORY));
            assertThat(gaps.getContent()).hasSize(ordered.size() - 1);
            for (int i = 0; i < gaps.getNumberOfElements(); i++) {
                final var gap = gaps.getContent().get(i);
                assertThat(gap.get("salesByFilmCategory", SalesByFilmCategory.class)).isEqualTo(ordered.get(i));
                assertThat(gap.get("nextSalesByFilmCategory", SalesByFilmCategory.class)).isEqualTo(ordered.get(i + 1));
                assertThat(gap.get("gap", BigDecimal.class)).isEqualByComparingTo(
                        ordered.get(i).getTotalSales().subtract(ordered.get(i + 1).getTotalSales()));
            }
            final var last = ordered.getLast().getCategory().equals(salesByFilmCategory.getCategory());
            assertThat(gaps.getContent())
                    .filteredOn(g -> g.get("salesByFilmCategory", SalesByFilmCategory.class)
                            .equals(salesByFilmCategory))
                    .hasSize(last ? 0 : 1);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private PaymentRepository paymentRepository;
}
