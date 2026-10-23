package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.PaymentRepository.CustomerPaymentTotal;
import com.github.jinahya.mysql.sakila.data.PaymentRepository.MonthPaymentTotal;
import com.github.jinahya.mysql.sakila.data.PaymentRepository.PaymentDayRun;
import com.github.jinahya.mysql.sakila.data.PaymentRepository.PaymentRunningTotal;
import com.github.jinahya.mysql.sakila.data.PaymentRepository.StaffPaymentTotal;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import com.github.jinahya.mysql.sakila.persistence.Payment;
import com.github.jinahya.mysql.sakila.persistence.Payment_;
import com.github.jinahya.mysql.sakila.persistence.Staff_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link PaymentRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few payments of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, against
 * counts and sums of the payments taken another way, or against the output upstream's
 * {@code payment_exercises_*.sql} records for the installed data. The base's own tests, such as its {@code findById}
 * round trips, run here too.
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
     * Sums the amounts of the payments the specified specification finds.
     *
     * @param specification the specification.
     * @return the sum of the amounts.
     */
    private BigDecimal sum(final Specification<Payment> specification) {
        return repositoryInstance().findAll(specification).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
     * Tests {@link PaymentRepository#selectTotalsByStaff()} ({@code SAKILA-PAYMENT-B-01}).
     */
    @Nested
    class SelectTotalsByStaff_Test {

        /**
         * Asserts, in one transaction, that the stream is the exercise's recorded output, the two staff members by
         * id, with their numbers of payments and their sums.
         */
        @Test
        @Transactional
        void __Transactional() {
            try (var stream = repositoryInstance().selectTotalsByStaff()) {
                final var list = stream.toList();
                assertThat(list)
                        .extracting(StaffPaymentTotal::getStaffId, StaffPaymentTotal::getPayments)
                        .containsExactly(tuple(1, 8054L), tuple(2, 7990L));
                assertThat(list)
                        .extracting(t -> t.getTotal().toPlainString())
                        .containsExactly("33482.50", "33924.06");
            }
        }

        /**
         * Asserts, in one transaction, that the stream is ordered by staff id, and that each count and each sum are
         * those of the payments a {@link Specification} on the staff member finds, which add up to all payments.
         */
        @Test
        @Transactional
        void __Ordered() {
            try (var stream = repositoryInstance().selectTotalsByStaff()) {
                final var list = stream.toList();
                assertThat(list)
                        .isSortedAccordingTo(Comparator.comparing(StaffPaymentTotal::getStaffId))
                        .allSatisfy(t -> {
                            final Specification<Payment> takenBy =
                                    (r, q, b) -> b.equal(r.get(Payment_.staff).get(Staff_.staffId), t.getStaffId());
                            assertThat(t.getPayments()).isEqualTo(repositoryInstance().count(takenBy));
                            assertThat(t.getTotal()).isEqualByComparingTo(sum(takenBy));
                        });
                assertThat(list.stream().mapToLong(StaffPaymentTotal::getPayments).sum())
                        .isEqualTo(repositoryInstance().count());
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         */
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

        private static Stream<Payment> payments() {
            return PaymentRepository_SpringBootIT.payments();
        }

        /**
         * Asserts that the list is the exercise's recorded output, five months, with their numbers of payments and
         * their sums.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectMonthlyTotals();
            assertThat(list)
                    .extracting(t -> "%d-%02d".formatted(t.getYear(), t.getMonth()),
                                MonthPaymentTotal::getPayments,
                                t -> t.getTotal().toPlainString())
                    .containsExactly(tuple("2005-05", 1156L, "4823.44"),
                                     tuple("2005-06", 2311L, "9629.89"),
                                     tuple("2005-07", 6709L, "28368.91"),
                                     tuple("2005-08", 5686L, "24070.14"),
                                     tuple("2006-02", 182L, "514.18"));
        }

        /**
         * Asserts that the list is ordered by the year and then by the month, and that each count and each sum are
         * those of the payments whose payment date falls in that month, counted again by a {@link Specification} on
         * the dates and added up again from the rows it finds.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectMonthlyTotals();
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparing(MonthPaymentTotal::getYear)
                                                 .thenComparing(MonthPaymentTotal::getMonth))
                    .allSatisfy(c -> {
                        final var paidIn = paidIn(c.getYear(), c.getMonth());
                        assertThat(c.getPayments()).as("payments in %d-%02d", c.getYear(), c.getMonth())
                                .isEqualTo(repositoryInstance().count(paidIn));
                        assertThat(c.getTotal()).as("total of %d-%02d", c.getYear(), c.getMonth())
                                .isEqualByComparingTo(sum(paidIn));
                    });
            assertThat(list.stream().mapToLong(MonthPaymentTotal::getPayments).sum())
                    .isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the month of a selected payment's payment date is among the months selected.
         *
         * @param payment one of the payments selected in {@link PaymentRepository_SpringBootIT#__()}.
         */
        @MethodSource("payments")
        @ParameterizedTest
        void __Selected(final Payment payment) {
            final var list = repositoryInstance().selectMonthlyTotals();
            final var paymentDate = payment.getPaymentDate();
            assertThat(list)
                    .anySatisfy(c -> {
                        assertThat(c.getYear()).isEqualTo(paymentDate.getYear());
                        assertThat(c.getMonth()).isEqualTo(paymentDate.getMonthValue());
                    });
        }
    }

    /**
     * Tests {@link PaymentRepository#countWithoutRental()} ({@code SAKILA-PAYMENT-B-03}).
     */
    @Nested
    class CountWithoutRental_Test {

        /**
         * Asserts that the count is the exercise's recorded output, zero, and the number of all payments minus those
         * with a rental, counted by a {@link Specification} of the opposite predicate, {@code isNotNull} on the
         * rental.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countWithoutRental();
            assertThat(count).isZero();
            final Specification<Payment> withRental = (r, q, b) -> b.isNotNull(r.get(Payment_.rental));
            assertThat(count).isEqualTo(repositoryInstance().count() - repositoryInstance().count(withRental));
        }
    }

    /**
     * Tests {@link PaymentRepository#listOfLargestAmount(Pageable)} ({@code SAKILA-PAYMENT-I-01}).
     */
    @Nested
    class ListOfLargestAmount_Test {

        private static Stream<Payment> payments() {
            return PaymentRepository_SpringBootIT.payments();
        }

        /**
         * Asserts that the page holds the exercise's recorded output: the largest amount, {@code 11.99}, paid by
         * {@code 10} payments in all.
         */
        @Test
        void __() {
            final var page = repositoryInstance().listOfLargestAmount(PageRequest.of(0, 5, BY_PAYMENT_ID));
            assertThat(page.getContent())
                    .isNotEmpty()
                    .allSatisfy(p -> assertThat(p.getAmount()).isEqualByComparingTo("11.99"));
            assertThat(page.getTotalElements()).isEqualTo(10L);
        }

        /**
         * Asserts that a page of the given size by id is ordered so, that every payment of it has the largest amount,
         * and that the payments are as many in all as a {@link Specification} on that amount counts.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __Ordered(final int size) {
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
         * Asserts that pages of three, by id, walk the same payments as one unpaged page.
         */
        @Test
        void __Paged() {
            final var all = repositoryInstance().listOfLargestAmount(Pageable.unpaged(BY_PAYMENT_ID));
            var page = repositoryInstance().listOfLargestAmount(PageRequest.of(0, 3, BY_PAYMENT_ID));
            final var walked = new ArrayList<>(page.getContent());
            while (page.hasNext()) {
                page = repositoryInstance().listOfLargestAmount(page.nextPageable());
                walked.addAll(page.getContent());
            }
            assertThat(walked).containsExactlyElementsOf(all.getContent());
        }

        /**
         * Asserts that a selected payment is listed exactly when its amount is the largest.
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
         * Asserts that more than {@code 180}, sorted the exercise's way, selects the exercise's recorded output, six
         * customers.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectCustomersWhoPaidMoreThan(
                    BigDecimal.valueOf(180L), PageRequest.of(0, 10, BY_TOTAL_DESC_THEN_CUSTOMER_ID));
            assertThat(page.getContent())
                    .extracting(CustomerPaymentTotal::getCustomerId, CustomerPaymentTotal::getFirstName,
                                CustomerPaymentTotal::getLastName, t -> t.getTotal().toPlainString())
                    .containsExactly(tuple(526, "KARL", "SEAL", "221.55"),
                                     tuple(148, "ELEANOR", "HUNT", "216.54"),
                                     tuple(144, "CLARA", "SHAW", "195.58"),
                                     tuple(137, "RHONDA", "KENNEDY", "194.61"),
                                     tuple(178, "MARION", "SNYDER", "194.61"),
                                     tuple(459, "TOMMY", "COLLAZO", "186.62"));
            assertThat(page.getTotalElements()).isEqualTo(6L);
        }

        /**
         * Asserts that, for the given threshold, the page is ordered by the total descending and then by the customer
         * id, that every total is more than the threshold, and that each total is the sum of the payments a
         * {@link Specification} on the customer finds.
         *
         * @param amount the threshold.
         */
        @ValueSource(ints = {150, 180, 200})
        @ParameterizedTest
        void __Ordered(final int amount) {
            final var threshold = BigDecimal.valueOf(amount);
            final var page = repositoryInstance().selectCustomersWhoPaidMoreThan(
                    threshold, PageRequest.of(0, 5, BY_TOTAL_DESC_THEN_CUSTOMER_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(CustomerPaymentTotal::getTotal).reversed()
                                                 .thenComparing(CustomerPaymentTotal::getCustomerId))
                    .allSatisfy(t -> {
                        assertThat(t.getTotal()).isGreaterThan(threshold);
                        final Specification<Payment> paidBy = (r, q, b) -> b.equal(
                                r.get(Payment_.customer).get(Customer_.customerId), t.getCustomerId());
                        assertThat(t.getTotal()).isEqualByComparingTo(sum(paidBy));
                    });
        }

        /**
         * Asserts that pages of four walk the same customers as one unpaged page, with the same total.
         */
        @Test
        void __Paged() {
            final var threshold = BigDecimal.valueOf(180L);
            final var all = repositoryInstance().selectCustomersWhoPaidMoreThan(
                    threshold, Pageable.unpaged(BY_TOTAL_DESC_THEN_CUSTOMER_ID));
            var page = repositoryInstance().selectCustomersWhoPaidMoreThan(
                    threshold, PageRequest.of(0, 4, BY_TOTAL_DESC_THEN_CUSTOMER_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            assertThat(page.getTotalPages()).isEqualTo(2);
            final var walked = new ArrayList<Integer>();
            page.forEach(t -> walked.add(t.getCustomerId()));
            while (page.hasNext()) {
                page = repositoryInstance().selectCustomersWhoPaidMoreThan(threshold, page.nextPageable());
                page.forEach(t -> walked.add(t.getCustomerId()));
            }
            assertThat(walked).containsExactlyElementsOf(
                    all.getContent().stream().map(CustomerPaymentTotal::getCustomerId).toList());
        }
    }

    /**
     * Tests {@link PaymentRepository#selectRunningTotals(Integer, Pageable)} ({@code SAKILA-PAYMENT-A-01}).
     */
    @Nested
    class SelectRunningTotals_Test {

        private static final Sort BY_PAYMENT_DATE_THEN_PAYMENT_ID = Sort.by("paymentDate", "paymentId");

        private static Stream<Payment> payments() {
            return PaymentRepository_SpringBootIT.payments();
        }

        /**
         * Asserts that the first ten payments of the customer {@code 1}, sorted the exercise's way, are the exercise's
         * recorded output, with their running totals.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectRunningTotals(
                    1, PageRequest.of(0, 10, BY_PAYMENT_DATE_THEN_PAYMENT_ID));
            assertThat(page.getContent())
                    .extracting(PaymentRunningTotal::getPaymentId,
                                t -> t.getAmount().toPlainString(),
                                t -> t.getRunningTotal().toPlainString())
                    .containsExactly(tuple(1, "2.99", "2.99"),
                                     tuple(2, "0.99", "3.98"),
                                     tuple(3, "5.99", "9.97"),
                                     tuple(4, "0.99", "10.96"),
                                     tuple(5, "9.99", "20.95"),
                                     tuple(6, "4.99", "25.94"),
                                     tuple(7, "4.99", "30.93"),
                                     tuple(8, "0.99", "31.92"),
                                     tuple(9, "3.99", "35.91"),
                                     tuple(10, "5.99", "41.90"));
        }

        /**
         * Asserts that all payments of the customer of a selected payment, sorted the exercise's way, are ordered so,
         * that each running total is the one before it plus the payment's amount, and that the last is the sum of all
         * the customer's payments, as many as a {@link Specification} on the customer counts.
         *
         * @param payment one of the payments selected in {@link PaymentRepository_SpringBootIT#__()}.
         */
        @MethodSource("payments")
        @ParameterizedTest
        @Transactional
        void __Ordered(final Payment payment) {
            final var customerId = repositoryInstance().findById(payment.getPaymentId()).orElseThrow()
                    .getCustomer().getCustomerId();
            final var all = repositoryInstance().selectRunningTotals(
                    customerId, Pageable.unpaged(BY_PAYMENT_DATE_THEN_PAYMENT_ID));
            assertThat(all.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(PaymentRunningTotal::getPaymentDate)
                                                 .thenComparing(PaymentRunningTotal::getPaymentId));
            var running = BigDecimal.ZERO;
            for (final var t : all) {
                running = running.add(t.getAmount());
                assertThat(t.getRunningTotal()).as("running total at %d", t.getPaymentId())
                        .isEqualByComparingTo(running);
            }
            final Specification<Payment> paidBy = (r, q, b) -> b.equal(
                    r.get(Payment_.customer).get(Customer_.customerId), customerId);
            assertThat(all.getTotalElements()).isEqualTo(repositoryInstance().count(paidBy));
            assertThat(running).isEqualByComparingTo(sum(paidBy));
        }

        /**
         * Asserts that the second page of ten continues the first: its rows are the eleventh to the twentieth of all,
         * with the same running totals.
         */
        @Test
        void __Paged() {
            final var all = repositoryInstance().selectRunningTotals(
                    1, Pageable.unpaged(BY_PAYMENT_DATE_THEN_PAYMENT_ID));
            final var second = repositoryInstance().selectRunningTotals(
                    1, PageRequest.of(1, 10, BY_PAYMENT_DATE_THEN_PAYMENT_ID));
            assertThat(second.getTotalElements()).isEqualTo(all.getTotalElements());
            final var expected = all.getContent().subList(10, Math.min(20, all.getContent().size()));
            assertThat(second.getContent())
                    .extracting(PaymentRunningTotal::getPaymentId, t -> t.getRunningTotal().toPlainString())
                    .containsExactlyElementsOf(expected.stream()
                                                       .map(t -> tuple(t.getPaymentId(),
                                                                       t.getRunningTotal().toPlainString()))
                                                       .toList());
        }
    }

    /**
     * Tests {@link PaymentRepository#selectRunsOfConsecutiveDays()} ({@code SAKILA-PAYMENT-A-02}).
     */
    @Nested
    class SelectRunsOfConsecutiveDays_Test {

        /**
         * Asserts that the native query selects the exercise's recorded output: six runs, by their first days.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectRunsOfConsecutiveDays();
            assertThat(list)
                    .extracting(PaymentDayRun::getFirstDay, PaymentDayRun::getLastDay, PaymentDayRun::getDays)
                    .containsExactly(tuple(LocalDate.of(2005, 5, 24), LocalDate.of(2005, 5, 31), 8L),
                                     tuple(LocalDate.of(2005, 6, 14), LocalDate.of(2005, 6, 21), 8L),
                                     tuple(LocalDate.of(2005, 7, 5), LocalDate.of(2005, 7, 12), 8L),
                                     tuple(LocalDate.of(2005, 7, 26), LocalDate.of(2005, 8, 2), 8L),
                                     tuple(LocalDate.of(2005, 8, 16), LocalDate.of(2005, 8, 23), 8L),
                                     tuple(LocalDate.of(2006, 2, 14), LocalDate.of(2006, 2, 14), 1L));
        }

        /**
         * Asserts that the runs are ordered by their first days, that each is as many days long as from its first day
         * to its last, and that no two runs touch.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectRunsOfConsecutiveDays();
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparing(PaymentDayRun::getFirstDay))
                    .allSatisfy(r -> assertThat(r.getDays())
                            .isEqualTo(ChronoUnit.DAYS.between(r.getFirstDay(), r.getLastDay()) + 1L));
            for (int i = 1; i < list.size(); i++) {
                assertThat(list.get(i).getFirstDay()).isAfter(list.get(i - 1).getLastDay().plusDays(1L));
            }
        }
    }
}
