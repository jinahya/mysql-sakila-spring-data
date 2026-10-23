package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.CustomerList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link CustomerListRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. The slice generates {@code customer_list} as a table, from the
 * entity, but every column of it except the id is read-only ({@code insertable = false}), so no row can be persisted
 * into it, and the fixtures do not try; nor can a customer, which needs a store. Each test here runs its query
 * against the empty table, and asserts an empty page, list or stream, or a zero: that proves the JPQL, the derived
 * query, or the native SQL, is valid for Hibernate and runs on H2, which is what a slice test of a view can prove.
 * What the queries return on the sample data is for {@link CustomerListRepository_SpringBootIT}.
 *
 * @see CustomerListRepository_SpringBootIT
 */
class CustomerListRepository_DataJpaTest
        extends _Repository_DataJpaTest<CustomerListRepository, CustomerList, Integer> {

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
     * Tests {@link CustomerListRepository#findAllByCountryOrderByIdAsc(String)} ({@code SAKILA-CUSTOMER_LIST-B-01}).
     */
    @Nested
    class FindAllByCountryOrderByIdAsc_Test {

        /**
         * Asserts that the country of the exercise, {@code Japan}, streams nothing from the empty table, inside the
         * test's transaction, as does a name no country has.
         */
        @Test
        void _Empty_Unknown() {
            try (var japan = repositoryInstance().findAllByCountryOrderByIdAsc("Japan")) {
                assertThat(japan).isEmpty();
            }
            try (var unknown = repositoryInstance().findAllByCountryOrderByIdAsc(randomName())) {
                assertThat(unknown).isEmpty();
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}, whatever the view holds.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByCountryOrderByIdAsc("Japan")) {
                    found.forEach(c -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} country is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullCountry() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByCountryOrderByIdAsc(null)) {
                    found.forEach(c -> {
                    });
                }
            }).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectCustomerCountsBySid(Pageable)}
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
     * Tests {@link CustomerListRepository#selectNotesCounts()} ({@code SAKILA-CUSTOMER_LIST-B-03}).
     */
    @Nested
    class SelectNotesCounts_Test {

        /**
         * Asserts that the grouped select, ordered by the notes in the query, runs on the empty table and selects an
         * empty list.
         */
        @Test
        void _Empty_Empty() {
            final var list = repositoryInstance().selectNotesCounts();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectRowCounts(Pageable)} ({@code SAKILA-CUSTOMER_LIST-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        private static final Sort BY_CUSTOMERS = Sort.by("customers");

        /**
         * Asserts that the select has its one row even over the empty view, and that both counts are zero: the view
         * is empty, and no customer can be saved into the slice.
         * <p>
         * The second count is a subquery in the select, on {@code Customer}; the test proves that Hibernate accepts it
         * there and that H2 runs it.
         */
        @Test
        void _Zero_Empty() {
            final var page = repositoryInstance().selectRowCounts(PageRequest.of(0, 1, BY_CUSTOMERS));
            assertThat(page.getContent()).singleElement().satisfies(c -> {
                assertThat(c.get("customerLists", Long.class)).isZero();
                assertThat(c.get("customers", Long.class)).isZero();
            });
        }

        /**
         * Asserts that the page after the one row is empty, and that the count query, which Spring Data runs for it,
         * counts the one row.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectRowCounts(PageRequest.of(1, 1, BY_CUSTOMERS));
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(1L);
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectRowCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectCitiesWithMoreCustomersThan(long, Pageable)}
     * ({@code SAKILA-CUSTOMER_LIST-I-02}).
     */
    @Nested
    class SelectCitiesWithMoreCustomersThan_Test {

        private static final Sort BY_CITY_THEN_COUNTRY = Sort.by("city").and(Sort.by("country"));

        /**
         * Asserts that the grouped select, with its {@code HAVING}, sorted as the exercise sorts, runs on the empty
         * view and selects an empty page, with no cities in all.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().selectCitiesWithMoreCustomersThan(
                    1L, PageRequest.of(0, 5, BY_CITY_THEN_COUNTRY));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a page past the first, which makes Spring Data run the count query, is empty too, with no
         * cities in all; that runs the count query, with its correlated subqueries, on the H2.
         */
        @Test
        void _Empty_Paged() {
            final var page = repositoryInstance().selectCitiesWithMoreCustomersThan(
                    1L, PageRequest.of(1, 1, BY_CITY_THEN_COUNTRY));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCitiesWithMoreCustomersThan(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectTopThreeCountriesOfEachStore()} ({@code SAKILA-CUSTOMER_LIST-A-01}).
     */
    @Nested
    class SelectTopThreeCountriesOfEachStore_Test {

        /**
         * Asserts that the native query, a {@code ROW_NUMBER()} over the grouped counts, runs on the H2 too, over the
         * table the slice generates for the view, and selects nothing from it.
         */
        @Test
        void _Empty_Empty() {
            final var list = repositoryInstance().selectTopThreeCountriesOfEachStore();
            assertThat(list).isEmpty();
        }
    }
}
