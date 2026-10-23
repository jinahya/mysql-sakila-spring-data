package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.CustomerListRepository.SidCustomerCount;
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
     * Tests {@link CustomerListRepository#listByCountry(String, Pageable)} ({@code SAKILA-CUSTOMER_LIST-B-01}).
     */
    @Nested
    class ListByCountry_Test {

        private static Stream<String> countries() {
            return CustomerListRepository_SpringBootIT.countries();
        }

        /**
         * Asserts that the country of a selected row lists rows of that country only, by id as the exercise sorts,
         * with the selected row among them, as many as the {@code customer} table has in that country along its
         * address chain, and that pages of two walk the same rows in the same order.
         *
         * @param country the country of one of the rows selected in {@link CustomerListRepository_SpringBootIT#__()}.
         */
        @MethodSource("countries")
        @ParameterizedTest
        void __(final String country) {
            final var all = repositoryInstance().listByCountry(country, Pageable.unpaged(BY_ID));
            assertThat(all.getContent())
                    .isNotEmpty()
                    .allSatisfy(c -> assertThat(c.getCountry()).isEqualToIgnoringCase(country))
                    .isSortedAccordingTo(Comparator.comparing(CustomerList::getId));
            assertThat(all.getContent()).extracting(CustomerList::getId)
                    .containsAnyElementsOf(entities.stream().map(CustomerList::getId).toList());
            assertThat(all.getTotalElements()).isEqualTo(countCustomersInCountry(country));
            var page = repositoryInstance().listByCountry(country, PageRequest.of(0, 2, BY_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            final var walked = new ArrayList<CustomerList>();
            while (true) {
                assertPage(page);
                walked.addAll(page.getContent());
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().listByCountry(country, page.nextPageable());
            }
            assertThat(walked).containsExactlyElementsOf(all.getContent());
        }

        /**
         * Asserts that the country of the exercise, {@code Japan}, lists customers, and a name no country has lists
         * none.
         */
        @ValueSource(strings = {"Japan"})
        @ParameterizedTest
        void __Japan(final String country) {
            final var japan = repositoryInstance().listByCountry(country, PageRequest.of(0, 5, BY_ID));
            assertPage(japan);
            assertThat(japan.getContent()).isNotEmpty()
                    .allSatisfy(c -> assertThat(c.getCountry()).isEqualToIgnoringCase(country));
            final var nonesuch = repositoryInstance().listByCountry("Nonesuch", PageRequest.of(0, 5, BY_ID));
            assertThat(nonesuch).isEmpty();
            assertThat(nonesuch.getTotalElements()).isZero();
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
                    .isSortedAccordingTo(Comparator.comparing(SidCustomerCount::getSid))
                    .allSatisfy(c -> {
                        assertThat(c.getCustomers()).as("customers of store %d", c.getSid())
                                .isEqualTo(countCustomersOfStore(c.getSid()));
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
                    .extracting(SidCustomerCount::getSid, SidCustomerCount::getCustomers)
                    .contains(tuple(sid, countCustomersOfStore(sid)));
        }
    }

    /**
     * Tests {@link CustomerListRepository#selectRowCounts()} ({@code SAKILA-CUSTOMER_LIST-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        private static Stream<Integer> sids() {
            return CustomerListRepository_SpringBootIT.sids();
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
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.getCustomerLists()).isPositive().isEqualTo(counts.getCustomers());
            assertThat(counts.getCustomerLists()).isEqualTo(repositoryInstance().count());
            assertThat(counts.getCustomers()).isEqualTo(customerRepository.count());
            final Specification<CustomerList> ofStore = (r, q, b) -> b.equal(r.get(CustomerList_.sid), sid);
            assertThat(repositoryInstance().count(ofStore)).isEqualTo(countCustomersOfStore(sid));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private CustomerRepository customerRepository;
}
