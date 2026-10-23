package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Payment;
import com.github.jinahya.mysql.sakila.persistence.Payment_;
import com.github.jinahya.mysql.sakila.persistence.SalesByStore;
import com.github.jinahya.mysql.sakila.persistence.Store;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link SalesByStoreRepository} against the Sakila database.
 * <p>
 * The repository is not a Spring Data one, so this does not extend {@link _Repository_SpringBootIT}: it is a plain
 * {@code @SpringBootTest}, over the context of {@link ___Spring_TestContext}, which {@code @Import}s the
 * implementation. There is no {@code @DataJpaTest} for it either: the slice's H2 is generated from the entities, and
 * the view does not exist there. Each repository call runs in a transaction of its own, and nothing here writes.
 * <p>
 * The fixture is every row of the view, one per store, selected once in {@link #__()}; each test tries every one of
 * them, or a few page sizes. What the view says is cross-checked through the repositories of the tables it is built
 * from: {@link StoreRepository} for the stores, and {@link PaymentRepository} for the payments it sums.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see SalesByStoreRepository
 * @see SalesByStoreRepositoryImpl
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class SalesByStoreRepository_SpringBootIT {

    private static List<SalesByStore> rows;

    /**
     * A sort by {@code store}, the view's natural key, which the implementation appends anyway.
     */
    private static final Sort BY_STORE = Sort.by(Sort.Direction.ASC, SalesByStore.ATTRIBUTE_NAME_STORE);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects every row of the view, by {@code store}, as the fixture for the parameterized tests.
     * <p>
     * Runs once per class, which {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the
     * autowired repository is already there; the result goes into a static field so that the static method sources of
     * the nested classes can reach it. The view has one row per store with a payment, so there are no more rows than
     * stores. An empty view aborts the tests of this class rather than failing them: it is the fixture that is
     * missing, not the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        rows = Collections.unmodifiableList(repository.listAll(Pageable.unpaged(BY_STORE)).getContent());
        rows.forEach(r -> log.debug("row: {}", r));
        assertThat(rows).hasSizeLessThanOrEqualTo((int) storeRepository.count());
        assumeThat(rows).isNotEmpty();
    }

    private static Stream<SalesByStore> rows() {
        return rows.stream();
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
     * Tests {@link SalesByStoreRepository#listAll(Pageable)} ({@code SAKILA-SALES_BY_STORE-B-01}).
     */
    @Nested
    class ListAll_Test {

        private static Stream<SalesByStore> rows() {
            return SalesByStoreRepository_SpringBootIT.rows();
        }

        /**
         * Asserts that a selected row says what the view defines: the store as its city and country, joined by a
         * comma, the manager as a first and a last name, joined by a space, and positive sales.
         *
         * @param row one of the rows selected in {@link SalesByStoreRepository_SpringBootIT#__()}.
         */
        @MethodSource("rows")
        @ParameterizedTest
        void __(final SalesByStore row) {
            assertThat(row.getStore()).matches("[^,]+,[^,]+");
            assertThat(row.getManager()).matches("\\S+ \\S+");
            assertThat(row.getTotalSales()).isPositive();
        }

        /**
         * Asserts that the first page of the given size, by {@code store}, is consistent with itself, ordered so, and
         * carries the number of fixture rows as its total.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 2, 5})
        @ParameterizedTest
        void __Sized(final int size) {
            final var page = repository.listAll(PageRequest.of(0, size, BY_STORE));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(rows.size());
            assertThat(page.getContent())
                    .isNotEmpty()
                    .hasSize(Math.min(size, rows.size()))
                    .isSortedAccordingTo(Comparator.comparing(SalesByStore::getStore, String.CASE_INSENSITIVE_ORDER));
        }

        /**
         * Asserts that a page sorted by the sales, descending, is ordered so, and then by {@code store}, which the
         * implementation appends.
         */
        @Test
        void __SortedByTotalSalesDesc() {
            final var sort = Sort.by(Sort.Direction.DESC, SalesByStore.ATTRIBUTE_NAME_TOTAL_SALES);
            final var page = repository.listAll(PageRequest.of(0, 5, sort));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(SalesByStore::getTotalSales).reversed()
                                                 .thenComparing(SalesByStore::getStore, String.CASE_INSENSITIVE_ORDER));
        }

        /**
         * Asserts that pages of one, walked one after another, add up to the single unpaged page, row by row, with the
         * same total.
         */
        @Test
        void __Paged() {
            final var all = repository.listAll(Pageable.unpaged(BY_STORE));
            assertThat(all.getTotalElements()).isEqualTo(all.getNumberOfElements());
            final var walked = new ArrayList<SalesByStore>();
            var page = repository.listAll(PageRequest.of(0, 1, BY_STORE));
            while (true) {
                assertPage(page);
                assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
                walked.addAll(page.getContent());
                if (!page.hasNext()) {
                    break;
                }
                page = repository.listAll(page.nextPageable());
            }
            assertThat(walked)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactlyElementsOf(all.getContent());
        }

        /**
         * Asserts that a sort by a property the view has no column for is rejected.
         * <p>
         * The implementation throws {@link IllegalArgumentException}, which {@code @Repository} has Spring translate
         * into {@link InvalidDataAccessApiUsageException} on the way out.
         */
        @Test
        void _InvalidDataAccessApiUsageException_UnknownSortProperty() {
            final var sort = Sort.by(Store.ATTRIBUTE_NAME_STORE_ID);
            assertThatThrownBy(() -> repository.listAll(PageRequest.of(0, 1, sort)))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link SalesByStoreRepository#sumTotalSales()} ({@code SAKILA-SALES_BY_STORE-I-01}).
     */
    @Nested
    class SumTotalSales_Test {

        /**
         * Asserts that the sum is the sum of the selected rows' sales, and is the sum of the amounts of the payments
         * which have a rental, read through {@link PaymentRepository}, as the exercise checks: the view reaches a
         * store through the rental, so a payment without one cannot be in it.
         */
        @Test
        void __() {
            final var sum = repository.sumTotalSales();
            log.debug("by store: {}", sum);
            assertThat(sum).isEqualByComparingTo(
                    rows.stream().map(SalesByStore::getTotalSales).reduce(BigDecimal.ZERO, BigDecimal::add));
            final Specification<Payment> specification = (r, q, b) -> b.isNotNull(r.get(Payment_.rental));
            final var withARental = paymentRepository.findAll(specification).stream()
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            log.debug("with a rental: {}", withARental);
            assertThat(sum).isEqualByComparingTo(withARental);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private SalesByStoreRepository repository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PaymentRepository paymentRepository;
}
