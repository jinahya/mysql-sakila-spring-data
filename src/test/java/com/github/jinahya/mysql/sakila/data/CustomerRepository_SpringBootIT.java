package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Customer;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import com.github.jinahya.mysql.sakila.persistence.Payment;
import com.github.jinahya.mysql.sakila.persistence.Payment_;
import com.github.jinahya.mysql.sakila.persistence.Rental;
import com.github.jinahya.mysql.sakila.persistence.Rental_;
import com.github.jinahya.mysql.sakila.persistence.Store;
import com.github.jinahya.mysql.sakila.persistence.Store_;
import jakarta.persistence.Tuple;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link CustomerRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few customers of
 * the sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or
 * against counts and sums taken again through the repositories of the other tables. The base's own tests, such as its
 * {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see CustomerRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerRepository_SpringBootIT
        extends _Repository_SpringBootIT<CustomerRepository, Customer, Integer> {

    private static List<Customer> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Customer_.customerId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_CUSTOMER_ID = Sort.by(Sort.Direction.ASC, Customer_.CUSTOMER_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    CustomerRepository_SpringBootIT() {
        super(CustomerRepository.class, Customer.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five customers, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The customers are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_CUSTOMER_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Customer> customers() {
        return entities.stream();
    }

    /**
     * Counts the customers whose {@code active} flag has the specified value, through a {@link Specification}.
     *
     * @param active the value of the flag.
     * @return the number of customers whose flag is {@code active}.
     */
    private long countByActive(final Boolean active) {
        final Specification<Customer> specification = (r, q, b) -> b.equal(r.get(Customer_.active), active);
        return repositoryInstance().count(specification);
    }

    /**
     * Counts the customers of the store of the specified id, through a {@link Specification}.
     *
     * @param storeId the id of the store.
     * @return the number of customers of the store.
     */
    private long countByStoreId(final Integer storeId) {
        final Specification<Customer> specification =
                (r, q, b) -> b.equal(r.get(Customer_.store).get(Store_.storeId), storeId);
        return repositoryInstance().count(specification);
    }

    /**
     * Sums the payments of the customer of the specified id, through {@link PaymentRepository}.
     * <p>
     * The customer's own {@code payments} is not public upstream, so the payments are read by a
     * {@link Specification}, on the id of the customer so that no customer instance is needed.
     *
     * @param customerId the id of the customer.
     * @return the sum of the amounts of the customer's payments; zero if there is none.
     */
    private BigDecimal sumPaymentsOf(final Integer customerId) {
        final Specification<Payment> specification =
                (r, q, b) -> b.equal(r.get(Payment_.customer).get(Customer_.customerId), customerId);
        return paymentRepository.findAll(specification).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Counts the rentals of the specified customer, through {@link RentalRepository}.
     *
     * @param customer the customer.
     * @return the number of rentals of {@code customer}.
     */
    private long countRentalsOf(final Customer customer) {
        final Specification<Rental> specification = (r, q, b) -> b.equal(r.get(Rental_.customer), customer);
        return rentalRepository.count(specification);
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
     * Tests {@link CustomerRepository#selectActiveCounts()} ({@code SAKILA-CUSTOMER-B-01}).
     */
    @Nested
    class SelectActiveCounts_Test {

        /**
         * Asserts, in one transaction, that the stream yields the two rows the exercise's SQL gives, {@code false}
         * first, as the query orders them, and that each count is the number of customers whose flag has that value,
         * counted again by a {@link Specification}, the two adding up to all customers.
         */
        @Transactional
        @Test
        void __() {
            final List<Tuple> list;
            try (var stream = repositoryInstance().selectActiveCounts()) {
                list = stream.toList();
            }
            assertThat(list)
                    .extracting(column("active", Boolean.class), column("customers", Long.class))
                    .containsExactly(tuple(false, 15L), tuple(true, 584L));
            assertThat(list).allSatisfy(c -> {
                final var active = c.get("active", Boolean.class);
                assertThat(c.get("customers", Long.class)).as("customers whose active is %s", active)
                        .isEqualTo(countByActive(active));
            });
            assertThat(list.stream().mapToLong(c -> c.get("customers", Long.class)).sum())
                    .isEqualTo(repositoryInstance().count());
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}.
         */
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var stream = repositoryInstance().selectActiveCounts()) {
                    stream.forEach(c -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link CustomerRepository#selectStoreCounts()} ({@code SAKILA-CUSTOMER-B-02}).
     */
    @Nested
    class SelectStoreCounts_Test {

        /**
         * Asserts that the list holds the two rows the exercise's SQL gives, by the store's id, as the query orders
         * them, and that each count is the number of customers of that store, counted again by a
         * {@link Specification} on the id of the store.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectStoreCounts();
            assertThat(list)
                    .extracting(column("store", Store.class, Store::getStoreId), column("customers", Long.class))
                    .containsExactly(tuple(1, 326L), tuple(2, 273L));
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparing(column("store", Store.class, Store::getStoreId)))
                    .allSatisfy(c -> {
                        final var storeId = c.get("store", Store.class).getStoreId();
                        assertThat(c.get("customers", Long.class)).as("customers of store %d", storeId)
                                .isEqualTo(countByStoreId(storeId));
                    });
        }

        /**
         * Asserts that the counts of all groups add up to the number of all customers: every customer has a store, so
         * every customer is in exactly one group.
         */
        @Test
        void __AddUp() {
            final var list = repositoryInstance().selectStoreCounts();
            final var sum = list.stream().mapToLong(c -> c.get("customers", Long.class)).sum();
            assertThat(sum).isEqualTo(repositoryInstance().count());
        }
    }

    /**
     * Tests {@link CustomerRepository#selectPaidTotals(Pageable)} ({@code SAKILA-CUSTOMER-I-01}).
     */
    @Nested
    class SelectPaidTotals_Test {

        private static final Sort BY_PAID_DESC_THEN_CUSTOMER_ID =
                Sort.by(Sort.Direction.DESC, "paid").and(Sort.by("c.customerId"));

        private static Stream<Customer> customers() {
            return CustomerRepository_SpringBootIT.customers();
        }

        /**
         * Asserts that a page of the given size, the biggest payer first as the exercise sorts, is ordered so, that
         * each customer is the one of its id, and that each sum is the sum of the customer's payments, added up again
         * from the rows of {@link PaymentRepository}.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectPaidTotals(
                    PageRequest.of(0, size, BY_PAID_DESC_THEN_CUSTOMER_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("paid", BigDecimal.class)).reversed()
                                                 .thenComparing(column("customer", Customer.class,
                                                                       Customer::getCustomerId)))
                    .allSatisfy(c -> {
                        final var customer = c.get("customer", Customer.class);
                        final var customerId = customer.getCustomerId();
                        assertThat(repositoryInstance().findById(customerId)).hasValue(customer);
                        assertThat(c.get("paid", BigDecimal.class)).as("paid by customer %d", customerId)
                                .isEqualByComparingTo(sumPaymentsOf(customerId));
                    });
        }

        /**
         * Asserts that the first five, by {@code paid} descending, then by {@code c.customerId}, are the five the
         * exercise's SQL gives.
         */
        @Test
        void __TopFive() {
            final var page = repositoryInstance().selectPaidTotals(PageRequest.of(0, 5, BY_PAID_DESC_THEN_CUSTOMER_ID));
            assertThat(page.getContent())
                    .extracting(column("customer", Customer.class, Customer::getCustomerId),
                                column("customer", Customer.class, Customer::getFirstName),
                                column("customer", Customer.class, Customer::getLastName),
                                c -> c.get("paid", BigDecimal.class).doubleValue())
                    .containsExactly(
                            tuple(526, "KARL", "SEAL", 221.55),
                            tuple(148, "ELEANOR", "HUNT", 216.54),
                            tuple(144, "CLARA", "SHAW", 195.58),
                            tuple(137, "RHONDA", "KENNEDY", 194.61),
                            tuple(178, "MARION", "SNYDER", 194.61));
        }

        /**
         * Asserts that a selected customer who paid at least once is among all rows, as the customer itself, with the
         * sum of their payments, and that one who never paid is not.
         *
         * @param customer one of the customers selected in {@link CustomerRepository_SpringBootIT#__()}.
         */
        @MethodSource("customers")
        @ParameterizedTest
        void __Selected(final Customer customer) {
            final var all = repositoryInstance().selectPaidTotals(Pageable.unpaged(BY_PAID_DESC_THEN_CUSTOMER_ID));
            final var paid = sumPaymentsOf(customer.getCustomerId());
            final var rows = all.getContent().stream()
                    .filter(c -> c.get("customer", Customer.class).equals(customer))
                    .toList();
            if (paid.signum() == 0) {
                assertThat(rows).isEmpty();
                return;
            }
            assertThat(rows).singleElement().satisfies(
                    c -> assertThat(c.get("paid", BigDecimal.class)).isEqualByComparingTo(paid));
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectPaidTotals(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerRepository#listNeverRented(Pageable)} ({@code SAKILA-CUSTOMER-I-02}).
     */
    @Nested
    class ListNeverRented_Test {

        /**
         * Asserts that a page of the given size by id holds customers with no rental in {@link RentalRepository}, and
         * that they are as many in all as the customers minus those whose rentals are not empty, counted by a
         * {@link Specification} of the opposite predicate.
         * <p>
         * The installed sample data has no such customer, so the page is expected to be empty there; the assertions
         * hold either way.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().listNeverRented(PageRequest.of(0, size, BY_CUSTOMER_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isSortedAccordingTo(Comparator.comparing(Customer::getCustomerId))
                    .allSatisfy(c -> assertThat(countRentalsOf(c)).isZero());
            final Specification<Customer> rented = (r, q, b) -> b.isNotEmpty(r.get(Customer_.rentals));
            assertThat(page.getTotalElements())
                    .isEqualTo(repositoryInstance().count() - repositoryInstance().count(rented));
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listNeverRented(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerRepository#selectTopThreePayersOfEachStore()} ({@code SAKILA-CUSTOMER-A-01}).
     */
    @Nested
    class SelectTopThreePayersOfEachStore_Test {

        /**
         * Asserts that the native query gives the six rows the exercise's SQL gives, in its order, and that each sum
         * is the sum of the customer's payments, added up again from the rows of {@link PaymentRepository}.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectTopThreePayersOfEachStore();
            assertThat(list)
                    .extracting(intColumn("store_id"), intColumn("customer_id"),
                                column("first_name", String.class), column("last_name", String.class),
                                c -> c.get("paid", BigDecimal.class).doubleValue())
                    .containsExactly(
                            tuple(1, 148, "ELEANOR", "HUNT", 216.54),
                            tuple(1, 144, "CLARA", "SHAW", 195.58),
                            tuple(1, 459, "TOMMY", "COLLAZO", 186.62),
                            tuple(2, 526, "KARL", "SEAL", 221.55),
                            tuple(2, 137, "RHONDA", "KENNEDY", 194.61),
                            tuple(2, 178, "MARION", "SNYDER", 194.61));
            assertThat(list).allSatisfy(c -> assertThat(c.get("paid", BigDecimal.class))
                    .isEqualByComparingTo(sumPaymentsOf(c.get("customer_id", Number.class).intValue())));
        }
    }

    /**
     * Tests {@link CustomerRepository#selectAboveAveragePayerCounts(Pageable)} ({@code SAKILA-CUSTOMER-A-02}).
     */
    @Nested
    class SelectAboveAveragePayerCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("s.storeId");

        /**
         * Asserts that the rows, by {@code s.storeId} as the exercise sorts, are the two the exercise's SQL gives, and
         * that the count query counts the two stores.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectAboveAveragePayerCounts(PageRequest.of(0, 5, BY_STORE_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .extracting(column("store", Store.class, Store::getStoreId), column("customers", Long.class),
                                c -> c.get("averagePaid", BigDecimal.class).doubleValue())
                    .containsExactly(
                            tuple(1, 154L, 113.49),
                            tuple(2, 126L, 111.39));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        /**
         * Asserts that pages of one, by {@code s.storeId} descending, hold the second store and then the first, two in
         * all, which the count query says.
         */
        @Test
        void __Paged() {
            final var sort = Sort.by(Sort.Direction.DESC, "s.storeId");
            final var page0 = repositoryInstance().selectAboveAveragePayerCounts(PageRequest.of(0, 1, sort));
            assertThat(page0.getContent())
                    .extracting(column("store", Store.class, Store::getStoreId))
                    .containsExactly(2);
            assertThat(page0.getTotalElements()).isEqualTo(2L);
            final var page1 = repositoryInstance().selectAboveAveragePayerCounts(page0.nextPageable());
            assertThat(page1.getContent())
                    .extracting(column("store", Store.class, Store::getStoreId))
                    .containsExactly(1);
            assertThat(page1.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectAboveAveragePayerCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RentalRepository rentalRepository;
}
