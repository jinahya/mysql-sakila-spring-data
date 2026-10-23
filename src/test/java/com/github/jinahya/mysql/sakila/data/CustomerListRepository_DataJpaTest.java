package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.CustomerList;
import com.github.jinahya.mysql.sakila.persistence.CustomerList_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link CustomerListRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. The slice generates {@code customer_list} as a table, from the
 * entity, but every column of it except the id is read-only ({@code insertable = false}), so no row can be persisted
 * into it, and the fixtures do not try; nor can a customer, which needs a store. Each test here runs its query
 * against the empty table, and asserts an empty page, or a zero: that proves the JPQL, or the criteria query, is
 * valid for Hibernate and runs on H2, which is what a slice test of a view can prove. What the queries return on the
 * sample data is for {@link CustomerListRepository_SpringBootIT}.
 *
 * @see CustomerListRepository_SpringBootIT
 */
class CustomerListRepository_DataJpaTest
        extends _Repository_DataJpaTest<CustomerListRepository, CustomerList, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code CustomerList_.id} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, CustomerList_.ID);

    /**
     * Creates a new instance.
     */
    CustomerListRepository_DataJpaTest() {
        super(CustomerListRepository.class, CustomerList.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link CustomerListRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The table is empty, so any id misses; the id is drawn from the whole {@code int} range all the same.
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
     * Tests {@link CustomerListRepository#listByCountry(String, org.springframework.data.domain.Pageable)}
     * ({@code SAKILA-CUSTOMER_LIST-B-01}).
     */
    @Nested
    class ListByCountry_Test {

        /**
         * Asserts that the country of the exercise, {@code Japan}, lists an empty page from the empty table, with no
         * elements in all, as does a name no country has.
         */
        @Test
        void _Empty_Unknown() {
            final var japan = repositoryInstance().listByCountry("Japan", PageRequest.of(0, 5, BY_ID));
            assertThat(japan).isEmpty();
            assertThat(japan.getTotalElements()).isZero();
            final var unknown = repositoryInstance().listByCountry(randomName(), PageRequest.of(0, 5, BY_ID));
            assertThat(unknown).isEmpty();
            assertThat(unknown.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectCustomerCountsBySid(org.springframework.data.domain.Pageable)}
     * ({@code SAKILA-CUSTOMER_LIST-B-02}).
     */
    @Nested
    class SelectCustomerCountsBySid_Test {

        private static final Sort BY_SID = Sort.by(Sort.Direction.ASC, "sid");

        /**
         * Asserts that the grouped select yields an empty page from the empty table, with no groups in all, which the
         * count query says.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().selectCustomerCountsBySid(PageRequest.of(0, 5, BY_SID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getTotalPages()).isZero();
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectRowCounts()} ({@code SAKILA-CUSTOMER_LIST-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        /**
         * Asserts that the select has its one row even over the empty view, and that both counts are zero: the view
         * is empty, and no customer can be saved into the slice.
         * <p>
         * The second count is a subquery in the select, on {@code Customer}; the test proves that Hibernate accepts it
         * there and that H2 runs it.
         */
        @Test
        void _Zero_Empty() {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts).isNotNull();
            assertThat(counts.getCustomerLists()).isZero();
            assertThat(counts.getCustomers()).isZero();
        }
    }
}
