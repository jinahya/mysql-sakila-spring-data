package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Customer;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link CustomerRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No customer can be persisted into the H2: a customer belongs to a
 * store, which names its manager, a staff member, who works at a store, both {@code NOT NULL}, so neither of the two
 * can be saved first, nor a customer after them (see {@link _Repository_DataJpaTest_Fixtures}). Each test here runs
 * its query against an empty {@code customer} table, and asserts an empty page or a zero; that still proves that the
 * JPQL, or the criteria query, is valid and runs on the H2, with the sort the exercise asks for.
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
     * Tests {@link CustomerRepository#selectActiveCounts(Pageable)} ({@code SAKILA-CUSTOMER-B-01}).
     */
    @Nested
    class SelectActiveCounts_Test {

        private static final Sort BY_ACTIVE = Sort.by("active");

        /**
         * Asserts that the grouped select, sorted by the flag as the exercise sorts, runs on the empty table and
         * selects an empty page, with no groups in all.
         */
        @Test
        void _Empty_NoCustomers() {
            final var page = repositoryInstance().selectActiveCounts(PageRequest.of(0, 5, BY_ACTIVE));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link CustomerRepository#selectStoreCounts(Pageable)} ({@code SAKILA-CUSTOMER-B-02}).
     */
    @Nested
    class SelectStoreCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("storeId");

        /**
         * Asserts that the grouped select, sorted by the store as the exercise sorts, runs on the empty table and
         * selects an empty page, with no groups in all.
         */
        @Test
        void _Empty_NoCustomers() {
            final var page = repositoryInstance().selectStoreCounts(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link CustomerRepository#selectPaidTotals(Pageable)} ({@code SAKILA-CUSTOMER-I-01}).
     */
    @Nested
    class SelectPaidTotals_Test {

        private static final Sort BY_PAID_DESC_THEN_CUSTOMER_ID =
                Sort.by(Sort.Direction.DESC, "paid").and(Sort.by("customerId"));

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
    }

    /**
     * Tests {@link CustomerRepository#listNeverRented(Pageable)} ({@code SAKILA-CUSTOMER-I-02}).
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
    }
}
