package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Payment;
import com.github.jinahya.mysql.sakila.persistence.Payment_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link PaymentRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No payment can be persisted into the H2: a payment is by a customer
 * of a store, taken by a staff member of a store, and a store names its manager, a staff member, who works at a store,
 * both {@code NOT NULL}, so none of them can be saved first (see {@link _Repository_DataJpaTest_Fixtures}). Each test
 * here runs its query against an empty {@code payment} table, and asserts an empty list, stream or page, or a zero;
 * that still proves that the JPQL, or the criteria query, is valid and runs on the H2, with the sort the exercise asks
 * for, and that the arguments are checked. {@link PaymentRepository#selectRunsOfConsecutiveDays()} is native-only,
 * MySQL's own SQL, so it has no test here; see {@link PaymentRepository_SpringBootIT}.
 *
 * @see PaymentRepository_SpringBootIT
 */
class PaymentRepository_DataJpaTest
        extends _Repository_DataJpaTest<PaymentRepository, Payment, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Payment_.paymentId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_PAYMENT_ID = Sort.by(Sort.Direction.ASC, Payment_.PAYMENT_ID);

    /**
     * Creates a new instance.
     */
    PaymentRepository_DataJpaTest() {
        super(PaymentRepository.class, Payment.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link PaymentRepository#findById(Object)}.
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
     * Tests {@link PaymentRepository#selectTotalsByStaff()} ({@code SAKILA-PAYMENT-B-01}).
     */
    @Nested
    class SelectTotalsByStaff_Test {

        /**
         * Asserts that the grouped select, ordered by staff id in the query, runs on the empty table and streams
         * nothing.
         */
        @Test
        void _Empty_NoPayments() {
            try (var stream = repositoryInstance().selectTotalsByStaff()) {
                assertThat(stream).isEmpty();
            }
        }

        /**
         * Runs the select with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened; the check runs before the query, and
         * throws {@link InvalidDataAccessApiUsageException} whether or not there is any payment.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var stream = repositoryInstance().selectTotalsByStaff()) {
                    stream.forEach(t -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link PaymentRepository#selectMonthlyTotals()} ({@code SAKILA-PAYMENT-B-02}).
     */
    @Nested
    class SelectMonthlyTotals_Test {

        /**
         * Asserts that the select grouped and ordered by the two {@code EXTRACT}s runs on the empty table and selects
         * an empty list.
         */
        @Test
        void _Empty_NoPayments() {
            final var list = repositoryInstance().selectMonthlyTotals();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link PaymentRepository#countWithoutRental()} ({@code SAKILA-PAYMENT-B-03}).
     */
    @Nested
    class CountWithoutRental_Test {

        /**
         * Asserts that the {@code isNull} criteria count runs on the empty table and counts zero.
         */
        @Test
        void _Zero_NoPayments() {
            final var count = repositoryInstance().countWithoutRental();
            assertThat(count).isZero();
        }
    }

    /**
     * Tests {@link PaymentRepository#listOfLargestAmount(Pageable)} ({@code SAKILA-PAYMENT-I-01}).
     */
    @Nested
    class ListOfLargestAmount_Test {

        /**
         * Asserts that the criteria query with its {@code MAX} subquery, sorted by id, runs on the empty table and
         * lists an empty page, with no payments in all.
         * <p>
         * On an empty table the subquery's {@code MAX} is {@code null}, which equals nothing, so the page is empty
         * rather than an error.
         */
        @Test
        void _Empty_NoPayments() {
            final var page = repositoryInstance().listOfLargestAmount(PageRequest.of(0, 5, BY_PAYMENT_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().listOfLargestAmount(PageRequest.of(1, 5, BY_PAYMENT_ID));
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
            assertThatThrownBy(() -> repositoryInstance().listOfLargestAmount(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link PaymentRepository#selectCustomersWhoPaidMoreThan(BigDecimal, Pageable)}
     * ({@code SAKILA-PAYMENT-I-02}).
     */
    @Nested
    class SelectCustomersWhoPaidMoreThan_Test {

        private static final Sort BY_TOTAL_DESC_THEN_CUSTOMER_ID =
                Sort.by(Sort.Direction.DESC, "total").and(Sort.by("customerId"));

        /**
         * Asserts that the select with its {@code HAVING}, sorted by the total descending and then by the customer id
         * as the exercise sorts, runs on the empty table and selects an empty page, with no customers in all.
         */
        @Test
        void _Empty_NoPayments() {
            final var page = repositoryInstance().selectCustomersWhoPaidMoreThan(
                    BigDecimal.valueOf(180L), PageRequest.of(0, 5, BY_TOTAL_DESC_THEN_CUSTOMER_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectCustomersWhoPaidMoreThan(
                    BigDecimal.ZERO, PageRequest.of(1, 5, BY_TOTAL_DESC_THEN_CUSTOMER_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} amount is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullAmount() {
            assertThatThrownBy(() -> repositoryInstance().selectCustomersWhoPaidMoreThan(
                    null, PageRequest.of(0, 5, BY_TOTAL_DESC_THEN_CUSTOMER_ID)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCustomersWhoPaidMoreThan(BigDecimal.ZERO, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link PaymentRepository#selectRunningTotals(Integer, Pageable)} ({@code SAKILA-PAYMENT-A-01}).
     */
    @Nested
    class SelectRunningTotals_Test {

        private static final Sort BY_PAYMENT_DATE_THEN_PAYMENT_ID = Sort.by("paymentDate", "paymentId");

        /**
         * Asserts that the self-joined select, sorted by the payment date and then by the payment id as the exercise
         * sorts, runs on the empty table and selects an empty page, with no payments in all.
         */
        @Test
        void _Empty_NoPayments() {
            final var page = repositoryInstance().selectRunningTotals(
                    1, PageRequest.of(0, 10, BY_PAYMENT_DATE_THEN_PAYMENT_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a second page is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectRunningTotals(
                    1, PageRequest.of(1, 10, BY_PAYMENT_DATE_THEN_PAYMENT_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} customer id is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullCustomerId() {
            assertThatThrownBy(() -> repositoryInstance().selectRunningTotals(
                    null, PageRequest.of(0, 10, BY_PAYMENT_DATE_THEN_PAYMENT_ID)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectRunningTotals(1, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
