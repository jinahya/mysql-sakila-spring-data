package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory;
import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
            assertThat(totals.getByCategory()).isNotNull().isPositive();
            assertThat(totals.getAllPayments()).isNotNull().isEqualByComparingTo(totals.getByCategory());
            final var summed = repositoryInstance().findAll(Pageable.unpaged()).stream()
                    .map(SalesByFilmCategory::getTotalSales)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totals.getByCategory()).isEqualByComparingTo(summed);
            assertThat(salesByFilmCategory.getTotalSales()).isLessThanOrEqualTo(totals.getByCategory());
            assertThat(paymentRepository.count()).isPositive();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private PaymentRepository paymentRepository;
}
