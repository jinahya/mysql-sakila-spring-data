package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Customer;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link CustomerRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No customer can be persisted into the H2: a customer belongs to a
 * store, which names its manager, a staff member, who works at a store, both {@code NOT NULL}, so neither of the two
 * can be saved first, nor a customer after them (see {@link _Repository_DataJpaTest_Fixtures}). Each test here runs
 * its query against an empty {@code customer} table, and asserts an empty page, list or stream; that still proves
 * that the JPQL, the criteria query, or the native SQL, is valid and runs on the H2, with the sort the exercise asks
 * for.
 *
 * @see CustomerRepository_SpringBootIT
 */
class CustomerRepository_DataJpaTest
        extends _Repository_DataJpaTest<CustomerRepository, Customer, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Customer_.customerId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_CUSTOMER_ID = Sort.by(Sort.Direction.ASC, Customer_.CUSTOMER_ID);

    /**
     * Creates a new instance.
     */
    CustomerRepository_DataJpaTest() {
        super(CustomerRepository.class, Customer.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link CustomerRepository#findById(Object)}.
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
     * Tests {@link CustomerRepository#selectActiveCounts()} ({@code SAKILA_CUSTOMER_B_01}).
     */
    @Nested
    class SelectActiveCounts_Test {

        /**
         * Asserts that the grouped select, ordered by the flag in the query, runs on the empty table, inside the
         * test's transaction, and streams nothing.
         */
        @Test
        void _Empty_NoCustomers() {
            try (var stream = repositoryInstance().selectActiveCounts()) {
                assertThat(stream).isEmpty();
            }
        }

        /**
         * Runs the select with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}, whatever the table holds.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
     * Tests {@link CustomerRepository#selectStoreCounts()} ({@code SAKILA_CUSTOMER_B_02}).
     */
    @Nested
    class SelectStoreCounts_Test {

        /**
         * Asserts that the grouped select, ordered by the store in the query, runs on the empty table and selects an
         * empty list.
         */
        @Test
        void _Empty_NoCustomers() {
            final var list = repositoryInstance().selectStoreCounts();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link CustomerRepository#selectPaidTotals(Pageable)} ({@code SAKILA_CUSTOMER_I_01}).
     */
    @Nested
    class SelectPaidTotals_Test {

        private static final Sort BY_PAID_DESC_THEN_CUSTOMER_ID =
                Sort.by(Sort.Direction.DESC, "paid").and(Sort.by("c.customerId"));

        /**
         * Asserts that the joined and grouped select, sorted by the sum and then by the id as the exercise sorts,
         * runs on the empty tables and selects an empty page, with no customers in all.
         */
        @Test
        void _Empty_NoCustomers() {
            final var page = repositoryInstance().selectPaidTotals(
                    PageRequest.of(0, 5, BY_PAID_DESC_THEN_CUSTOMER_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectPaidTotals(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerRepository#listNeverRented(Pageable)} ({@code SAKILA_CUSTOMER_I_02}).
     */
    @Nested
    class ListNeverRented_Test {

        /**
         * Asserts that the {@code isEmpty} criteria query, sorted by id, runs on the empty tables and lists an empty
         * page, with no customers in all.
         */
        @Test
        void _Empty_NoCustomers() {
            final var page = repositoryInstance().listNeverRented(PageRequest.of(0, 5, BY_CUSTOMER_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listNeverRented(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerRepository#selectTopThreePayersOfEachStore()} ({@code SAKILA_CUSTOMER_A_01}).
     */
    @Nested
    class SelectTopThreePayersOfEachStore_Test {

        /**
         * Asserts that the native query, a {@code ROW_NUMBER()} over the grouped sums, runs on the H2 too, and selects
         * nothing from the empty tables.
         */
        @Test
        void _Empty_NoCustomers() {
            final var list = repositoryInstance().selectTopThreePayersOfEachStore();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link CustomerRepository#selectAboveAveragePayerCounts(Pageable)} ({@code SAKILA_CUSTOMER_A_02}).
     */
    @Nested
    class SelectAboveAveragePayerCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("s.storeId");

        /**
         * Asserts that the select, with its correlated sums, sorted by the store as the exercise sorts, runs on the
         * empty tables and selects an empty page, with no stores in all.
         */
        @Test
        void _Empty_NoCustomers() {
            final var page = repositoryInstance().selectAboveAveragePayerCounts(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a page past the first, which makes Spring Data run the count query, is empty too, with no
         * stores in all; that runs the count query on the H2.
         */
        @Test
        void _Empty_Paged() {
            final var page = repositoryInstance().selectAboveAveragePayerCounts(PageRequest.of(1, 1, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectAboveAveragePayerCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
