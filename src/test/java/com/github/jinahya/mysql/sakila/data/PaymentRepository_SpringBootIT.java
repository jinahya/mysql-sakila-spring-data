package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.PaymentRepository.MonthPaymentTotal;
import com.github.jinahya.mysql.sakila.persistence.Payment;
import com.github.jinahya.mysql.sakila.persistence.Payment_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link PaymentRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few payments of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * counts and sums taken again by a {@link Specification}. The base's own tests, such as its {@code findById} round
 * trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see PaymentRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentRepository_SpringBootIT
        extends _Repository_SpringBootIT<PaymentRepository, Payment, Integer> {

    private static List<Payment> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Payment_.paymentId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_PAYMENT_ID = Sort.by(Sort.Direction.ASC, Payment_.PAYMENT_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    PaymentRepository_SpringBootIT() {
        super(PaymentRepository.class, Payment.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five payments, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The payments are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_PAYMENT_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Payment> payments() {
        return entities.stream();
    }

    /**
     * Returns a {@link Specification} of the payments made in the specified month: at or after the first instant of
     * the month, and before the first instant of the next.
     *
     * @param year  the year of the month.
     * @param month the month of the year.
     * @return a specification of the payments made in the month.
     */
    private static Specification<Payment> paidIn(final int year, final int month) {
        final var start = LocalDateTime.of(year, month, 1, 0, 0);
        final var end = start.plusMonths(1L);
        return (r, q, b) -> b.and(
                b.greaterThanOrEqualTo(r.get(Payment_.paymentDate), start),
                b.lessThan(r.get(Payment_.paymentDate), end)
        );
    }

    /**
     * Returns the largest amount of all payments, as the first of all payments sorted by amount descending.
     *
     * @return the largest amount.
     */
    private BigDecimal largestAmount() {
        final var sort = JpaSort.of(Sort.Direction.DESC, Payment_.amount);
        final var first = repositoryInstance().findAll(PageRequest.of(0, 1, sort)).getContent();
        assertThat(first).isNotEmpty();
        return first.getFirst().getAmount();
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
     * Tests {@link PaymentRepository#selectMonthlyTotals(Pageable)} ({@code SAKILA-PAYMENT-B-02}).
     */
    @Nested
    class SelectMonthlyTotals_Test {

        private static final Sort BY_YEAR_THEN_MONTH = Sort.by("year", "month");

        /**
         * Asserts that a page of the given size, by the year and then by the month as the exercise sorts, is ordered
         * so, that each month is a month, and that each count and each sum are those of the payments whose payment
         * date falls in that month, counted again by a {@link Specification} on the dates and added up again from the
         * rows it finds.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 3, 12})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectMonthlyTotals(PageRequest.of(0, size, BY_YEAR_THEN_MONTH));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(MonthPaymentTotal::getYear)
                                                 .thenComparing(MonthPaymentTotal::getMonth))
                    .allSatisfy(c -> {
                        assertThat(c.getMonth()).isBetween(1, 12);
                        final var paidIn = paidIn(c.getYear(), c.getMonth());
                        assertThat(c.getPayments()).as("payments in %d-%02d", c.getYear(), c.getMonth())
                                .isEqualTo(repositoryInstance().count(paidIn));
                        final var total = repositoryInstance().findAll(paidIn).stream()
                                .map(Payment::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        assertThat(c.getTotal()).as("total of %d-%02d", c.getYear(), c.getMonth())
                                .isEqualByComparingTo(total);
                    });
        }

        /**
         * Asserts that the counts of all months add up to the number of all payments, and that the months are as
         * many in all as the count query says: every payment has a payment date, so every payment is in exactly one
         * month.
         */
        @Test
        void __AddUp() {
            final var all = repositoryInstance().selectMonthlyTotals(Pageable.unpaged(BY_YEAR_THEN_MONTH));
            assertThat(all.getContent()).hasSize((int) all.getTotalElements());
            final var sum = all.getContent().stream().mapToLong(MonthPaymentTotal::getPayments).sum();
            assertThat(sum).isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the month of a selected payment's payment date is among the months selected.
         *
         * @param payment one of the payments selected in {@link PaymentRepository_SpringBootIT#__()}.
         */
        @MethodSource("payments")
        @ParameterizedTest
        void __Selected(final Payment payment) {
            final var all = repositoryInstance().selectMonthlyTotals(Pageable.unpaged(BY_YEAR_THEN_MONTH));
            final var paymentDate = payment.getPaymentDate();
            assertThat(all.getContent())
                    .anySatisfy(c -> {
                        assertThat(c.getYear()).isEqualTo(paymentDate.getYear());
                        assertThat(c.getMonth()).isEqualTo(paymentDate.getMonthValue());
                    });
        }

        private static Stream<Payment> payments() {
            return PaymentRepository_SpringBootIT.payments();
        }
    }

    /**
     * Tests {@link PaymentRepository#countWithoutRental()} ({@code SAKILA-PAYMENT-B-03}).
     */
    @Nested
    class CountWithoutRental_Test {

        /**
         * Asserts that the count is the number of all payments minus those with a rental, counted by a
         * {@link Specification} of the opposite predicate, {@code isNotNull} on the rental.
         * <p>
         * The installed sample data has no payment without a rental, so the count is expected to be zero there; the
         * assertion holds either way.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countWithoutRental();
            final Specification<Payment> withRental = (r, q, b) -> b.isNotNull(r.get(Payment_.rental));
            assertThat(count).isEqualTo(repositoryInstance().count() - repositoryInstance().count(withRental));
        }
    }

    /**
     * Tests {@link PaymentRepository#listOfLargestAmount(Pageable)} ({@code SAKILA-PAYMENT-I-01}).
     */
    @Nested
    class ListOfLargestAmount_Test {

        /**
         * Asserts that a page of the given size by id is ordered so, that every payment of it has the largest amount,
         * which is the amount of the first of all payments sorted by amount descending, and that the payments are as
         * many in all as a {@link Specification} on that amount counts: the exercise's two columns.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var largest = largestAmount();
            final var page = repositoryInstance().listOfLargestAmount(PageRequest.of(0, size, BY_PAYMENT_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(Payment::getPaymentId))
                    .allSatisfy(p -> assertThat(p.getAmount()).isEqualByComparingTo(largest));
            final Specification<Payment> ofLargest = (r, q, b) -> b.equal(r.get(Payment_.amount), largest);
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count(ofLargest));
        }

        /**
         * Asserts that a selected payment's amount is no larger than the largest, and that the payment is listed
         * exactly when its amount is the largest.
         *
         * @param payment one of the payments selected in {@link PaymentRepository_SpringBootIT#__()}.
         */
        @MethodSource("payments")
        @ParameterizedTest
        void __Selected(final Payment payment) {
            final var all = repositoryInstance().listOfLargestAmount(Pageable.unpaged(BY_PAYMENT_ID));
            final var largest = largestAmount();
            assertThat(payment.getAmount()).isLessThanOrEqualTo(largest);
            if (payment.getAmount().compareTo(largest) == 0) {
                assertThat(all.getContent()).contains(payment);
            } else {
                assertThat(all.getContent()).doesNotContain(payment);
            }
        }

        private static Stream<Payment> payments() {
            return PaymentRepository_SpringBootIT.payments();
        }
    }
}
