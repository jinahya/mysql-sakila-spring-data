package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Address_;
import com.github.jinahya.mysql.sakila.persistence.City_;
import com.github.jinahya.mysql.sakila.persistence.Country_;
import com.github.jinahya.mysql.sakila.persistence.Customer;
import com.github.jinahya.mysql.sakila.persistence.CustomerList;
import com.github.jinahya.mysql.sakila.persistence.CustomerList_;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import com.github.jinahya.mysql.sakila.persistence.Store_;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link CustomerListRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rows of the
 * view, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against the
 * {@code customer} table the view is built on, read through {@link CustomerRepository}. The base's own tests, such as
 * its {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see CustomerListRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerListRepository_SpringBootIT
        extends _Repository_SpringBootIT<CustomerListRepository, CustomerList, Integer> {

    private static List<CustomerList> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code CustomerList_.id} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, CustomerList_.ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    CustomerListRepository_SpringBootIT() {
        super(CustomerListRepository.class, CustomerList.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rows of the view, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones of the first customers the init scripts put there. Runs once per
     * class, which {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired
     * repository is already there; the result goes into a static field so that the static method sources of the
     * nested classes can reach it. The rows are loaded outside any transaction, so they are detached.
     * <p>
     * An empty view aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<String> countries() {
        return entities.stream().map(CustomerList::getCountry).distinct();
    }

    private static Stream<Integer> sids() {
        return entities.stream().map(CustomerList::getSid).distinct();
    }

    /**
     * Counts the customers of the {@code customer} table whose store has the specified id, through
     * {@link CustomerRepository}.
     * <p>
     * The store's own {@code customers} is not public upstream, so the customers are counted by a
     * {@link Specification}, through the customer's {@code store}.
     *
     * @param sid the id of the store.
     * @return the number of customers of the store.
     */
    private long countCustomersOfStore(final int sid) {
        final Specification<Customer> specification =
                (r, q, b) -> b.equal(r.get(Customer_.store).get(Store_.storeId), sid);
        return customerRepository.count(specification);
    }

    /**
     * Counts the customers of the {@code customer} table whose address is in the specified country, through
     * {@link CustomerRepository}, along the address chain the view joins.
     *
     * @param country the name of the country.
     * @return the number of customers in the country.
     */
    private long countCustomersInCountry(final String country) {
        final Specification<Customer> specification = (r, q, b) -> b.equal(
                r.get(Customer_.address).get(Address_.city).get(City_.country).get(Country_.country), country);
        return customerRepository.count(specification);
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
     * Tests {@link CustomerListRepository#findAllByCountryOrderByIdAsc(String)} ({@code SAKILA-CUSTOMER_LIST-B-01}).
     */
    @Nested
    class FindAllByCountryOrderByIdAsc_Test {

        private static Stream<String> countries() {
            return CustomerListRepository_SpringBootIT.countries();
        }

        /**
         * Asserts, in one transaction, that the country of the exercise, {@code Japan}, streams the thirty-one rows the
         * exercise's SQL gives, by id, and that a name no country has streams none.
         */
        @Transactional
        @Test
        void __Japan() {
            try (var japan = repositoryInstance().findAllByCountryOrderByIdAsc("Japan")) {
                final var list = japan.toList();
                assertThat(list).hasSize(31);
                assertThat(list)
                        .extracting(CustomerList::getId, CustomerList::getName)
                        .startsWith(tuple(1, "MARY SMITH"), tuple(11, "LISA ANDERSON"), tuple(29, "ANGELA HERNANDEZ"))
                        .endsWith(tuple(574, "JULIAN VEST"));
            }
            try (var nonesuch = repositoryInstance().findAllByCountryOrderByIdAsc("Nonesuch")) {
                assertThat(nonesuch).isEmpty();
            }
        }

        /**
         * Asserts, in one transaction, that the country of a selected row streams rows of that country only, by id as
         * the query orders them, the selected row among them, as many as the {@code customer} table has in that
         * country along its address chain.
         *
         * @param country the country of one of the rows selected in {@link CustomerListRepository_SpringBootIT#__()}.
         */
        @Transactional
        @MethodSource("countries")
        @ParameterizedTest
        void __(final String country) {
            try (var stream = repositoryInstance().findAllByCountryOrderByIdAsc(country)) {
                final var list = stream.toList();
                assertThat(list)
                        .isNotEmpty()
                        .allSatisfy(c -> assertThat(c.getCountry()).isEqualToIgnoringCase(country))
                        .isSortedAccordingTo(Comparator.comparing(CustomerList::getId));
                assertThat(list).extracting(CustomerList::getId)
                        .containsAnyElementsOf(entities.stream().map(CustomerList::getId).toList());
                assertThat((long) list.size()).isEqualTo(countCustomersInCountry(country));
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}.
         *
         * @param country the country of one of the rows selected in {@link CustomerListRepository_SpringBootIT#__()}.
         */
        @MethodSource("countries")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final String country) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByCountryOrderByIdAsc(country)) {
                    found.forEach(c -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} country is rejected before any query runs.
         */
        @Transactional
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
     * Tests {@link CustomerListRepository#selectCustomerCountsBySid(Pageable)} ({@code SAKILA-CUSTOMER_LIST-B-02}).
     */
    @Nested
    class SelectCustomerCountsBySid_Test {

        private static final Sort BY_SID = Sort.by(Sort.Direction.ASC, "sid");

        private static Stream<Integer> sids() {
            return CustomerListRepository_SpringBootIT.sids();
        }

        /**
         * Asserts that a page of the given size, by {@code sid} as the exercise sorts, is ordered so, that each count
         * is the number of customers of that store in the {@code customer} table, and that the groups are as many as
         * the distinct {@code SID}s of the view.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 2, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectCustomerCountsBySid(PageRequest.of(0, size, BY_SID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("sid", Integer.class)))
                    .allSatisfy(c -> {
                        final var sid = c.get("sid", Integer.class);
                        assertThat(c.get("customers", Long.class)).as("customers of store %d", sid)
                                .isEqualTo(countCustomersOfStore(sid));
                    });
            final var distinctSids = repositoryInstance().findAll(Pageable.unpaged()).stream()
                    .map(CustomerList::getSid)
                    .distinct()
                    .count();
            assertThat(page.getTotalElements()).isEqualTo(distinctSids);
        }

        /**
         * Asserts that the store of a selected row is one of the groups, with as many customers as the store has.
         *
         * @param sid the {@code SID} of one of the rows selected in {@link CustomerListRepository_SpringBootIT#__()}.
         */
        @MethodSource("sids")
        @ParameterizedTest
        void __Selected(final int sid) {
            final var all = repositoryInstance().selectCustomerCountsBySid(Pageable.unpaged(BY_SID));
            assertThat(all.getContent())
                    .extracting(column("sid", Integer.class), column("customers", Long.class))
                    .contains(tuple(sid, countCustomersOfStore(sid)));
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectNotesCounts()} ({@code SAKILA-CUSTOMER_LIST-B-03}).
     */
    @Nested
    class SelectNotesCounts_Test {

        /**
         * Asserts that the list holds the two rows the exercise's SQL gives, the empty notes first, as the query
         * orders them, that the {@code active} count is the number of active customers of the {@code customer} table,
         * and that the two add up to the rows of the view.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectNotesCounts();
            assertThat(list)
                    .extracting(column("notes", String.class), column("customers", Long.class))
                    .containsExactly(tuple("", 15L), tuple("active", 584L));
            final Specification<Customer> active = (r, q, b) -> b.isTrue(r.get(Customer_.active));
            assertThat(list.getLast().get("customers", Long.class)).isEqualTo(customerRepository.count(active));
            assertThat(list.stream().mapToLong(c -> c.get("customers", Long.class)).sum())
                    .isEqualTo(repositoryInstance().count());
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectRowCounts(Pageable)} ({@code SAKILA-CUSTOMER_LIST-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        private static final Sort BY_CUSTOMERS = Sort.by("customers");

        private static Stream<Integer> sids() {
            return CustomerListRepository_SpringBootIT.sids();
        }

        /**
         * Asserts that the one row holds the two counts the exercise's SQL gives, equal, as the exercise checks, and
         * that a page past it is empty, with the one row in all, which the count query says.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectRowCounts(PageRequest.of(0, 1, BY_CUSTOMERS));
            assertPage(page);
            assertThat(page.getContent())
                    .extracting(column("customerLists", Long.class), column("customers", Long.class))
                    .containsExactly(tuple(599L, 599L));
            final var next = repositoryInstance().selectRowCounts(PageRequest.of(1, 1, BY_CUSTOMERS));
            assertThat(next.getContent()).isEmpty();
            assertThat(next.getTotalElements()).isEqualTo(1L);
        }

        /**
         * Asserts that the view has as many rows as the {@code customer} table, as the exercise checks, that each
         * count is what the view's own repository, and {@link CustomerRepository}, count, and that the customers the
         * view puts in a selected row's store are as many as the table has there.
         *
         * @param sid the {@code SID} of one of the rows selected in {@link CustomerListRepository_SpringBootIT#__()}.
         */
        @MethodSource("sids")
        @ParameterizedTest
        void __(final int sid) {
            final var counts = repositoryInstance().selectRowCounts(Pageable.unpaged()).getContent().getFirst();
            final var customerLists = counts.get("customerLists", Long.class);
            final var customers = counts.get("customers", Long.class);
            assertThat(customerLists).isPositive().isEqualTo(customers);
            assertThat(customerLists).isEqualTo(repositoryInstance().count());
            assertThat(customers).isEqualTo(customerRepository.count());
            final Specification<CustomerList> ofStore = (r, q, b) -> b.equal(r.get(CustomerList_.sid), sid);
            assertThat(repositoryInstance().count(ofStore)).isEqualTo(countCustomersOfStore(sid));
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
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
         * Asserts that more than one customer, by {@code city}, then by {@code country}, as the exercise sorts, selects
         * the two cities the exercise's SQL gives, and that each count is what a {@link Specification} on the city and
         * the country counts in the view.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectCitiesWithMoreCustomersThan(
                    1L, Pageable.unpaged(BY_CITY_THEN_COUNTRY));
            assertThat(page.getContent())
                    .extracting(column("city", String.class), column("country", String.class),
                                column("customers", Long.class))
                    .containsExactly(
                            tuple("Aurora", "United States", 2L),
                            tuple("London", "United Kingdom", 2L));
            assertThat(page.getContent()).allSatisfy(c -> {
                final Specification<CustomerList> in = (r, q, b) -> b.and(
                        b.equal(r.get(CustomerList_.city), c.get("city", String.class)),
                        b.equal(r.get(CustomerList_.country), c.get("country", String.class)));
                assertThat(repositoryInstance().count(in)).isEqualTo(c.get("customers", Long.class));
            });
        }

        /**
         * Asserts that pages of one walk the same two cities, two in all, which the count query says, and that no city
         * has more than two customers.
         */
        @Test
        void __Paged() {
            final var page0 = repositoryInstance().selectCitiesWithMoreCustomersThan(
                    1L, PageRequest.of(0, 1, BY_CITY_THEN_COUNTRY));
            assertThat(page0.getContent()).extracting(column("city", String.class)).containsExactly("Aurora");
            assertThat(page0.getTotalElements()).isEqualTo(2L);
            final var page1 = repositoryInstance().selectCitiesWithMoreCustomersThan(1L, page0.nextPageable());
            assertThat(page1.getContent()).extracting(column("city", String.class)).containsExactly("London");
            assertThat(page1.hasNext()).isFalse();
            final var none = repositoryInstance().selectCitiesWithMoreCustomersThan(
                    2L, PageRequest.of(0, 1, BY_CITY_THEN_COUNTRY));
            assertThat(none).isEmpty();
            assertThat(none.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
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
         * Asserts that the native query gives the six rows the exercise's SQL gives, in its order, and that each count
         * is what a {@link Specification} on the store and the country counts in the view.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectTopThreeCountriesOfEachStore();
            assertThat(list)
                    .extracting(intColumn("sid"), column("country", String.class), longColumn("customers"))
                    .containsExactly(
                            tuple(1, "India", 37L),
                            tuple(1, "China", 26L),
                            tuple(1, "United States", 22L),
                            tuple(2, "China", 27L),
                            tuple(2, "India", 23L),
                            tuple(2, "Mexico", 15L));
            assertThat(list).allSatisfy(c -> {
                final Specification<CustomerList> in = (r, q, b) -> b.and(
                        b.equal(r.get(CustomerList_.sid), c.get("sid", Number.class).intValue()),
                        b.equal(r.get(CustomerList_.country), c.get("country", String.class)));
                assertThat(repositoryInstance().count(in)).isEqualTo(c.get("customers", Number.class).longValue());
            });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private CustomerRepository customerRepository;
}
