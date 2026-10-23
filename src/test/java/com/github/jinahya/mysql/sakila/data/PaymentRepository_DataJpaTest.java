package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Payment;
import com.github.jinahya.mysql.sakila.persistence.Payment_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link PaymentRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No payment can be persisted into the H2: a payment is by a customer
 * of a store, taken by a staff member of a store, and a store names its manager, a staff member, who works at a store,
 * both {@code NOT NULL}, so none of them can be saved first (see {@link _Repository_DataJpaTest_Fixtures}). Each test
 * here runs its query against an empty {@code payment} table, and asserts an empty page or a zero; that still proves
 * that the JPQL, or the criteria query, is valid and runs on the H2, with the sort the exercise asks for.
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
     * Tests {@link PaymentRepository#selectMonthlyTotals(Pageable)} ({@code SAKILA-PAYMENT-B-02}).
     */
    @Nested
    class SelectMonthlyTotals_Test {

        private static final Sort BY_YEAR_THEN_MONTH = Sort.by("year", "month");

        /**
         * Asserts that the select grouped by the two {@code EXTRACT}s, sorted by the year and then by the month as the
         * exercise sorts, runs on the empty table and selects an empty page, with no months in all.
         */
        @Test
        void _Empty_NoPayments() {
            final var page = repositoryInstance().selectMonthlyTotals(PageRequest.of(0, 5, BY_YEAR_THEN_MONTH));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
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
    }
}
