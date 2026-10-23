package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.CountryRepository.CountryCustomerCount;
import com.github.jinahya.mysql.sakila.data.CountryRepository.CountryCustomerRanking;
import com.github.jinahya.mysql.sakila.data.CountryRepository.CountryRunningTotal;
import com.github.jinahya.mysql.sakila.persistence.Address_;
import com.github.jinahya.mysql.sakila.persistence.City;
import com.github.jinahya.mysql.sakila.persistence.City_;
import com.github.jinahya.mysql.sakila.persistence.Country;
import com.github.jinahya.mysql.sakila.persistence.Country_;
import com.github.jinahya.mysql.sakila.persistence.Customer;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link CountryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few countries of
 * the sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or
 * against the rows the exercise's SQL gives on the installed data, or against counts taken again through the
 * repositories of the other tables. The base's own tests, such as its {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see CountryRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CountryRepository_SpringBootIT
        extends _Repository_SpringBootIT<CountryRepository, Country, Integer> {

    private static List<Country> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Country_.countryId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_COUNTRY_ID = Sort.by(Sort.Direction.ASC, Country_.COUNTRY_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    CountryRepository_SpringBootIT() {
        super(CountryRepository.class, Country.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five countries, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The countries are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_COUNTRY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Country> countries() {
        return entities.stream();
    }

    /**
     * Counts the cities of the specified country, through {@link CityRepository}.
     *
     * @param country the country.
     * @return the number of cities of {@code country}.
     */
    private long countCitiesOf(final Country country) {
        final Specification<City> specification = (r, q, b) -> b.equal(r.get(City_.country), country);
        return cityRepository.count(specification);
    }

    /**
     * Counts the customers whose address is in the specified country, through {@link CustomerRepository}, along the
     * address chain.
     *
     * @param country the country.
     * @return the number of customers in {@code country}.
     */
    private long countCustomersIn(final Country country) {
        final Specification<Customer> specification = (r, q, b) -> b.equal(
                r.get(Customer_.address).get(Address_.city).get(City_.country), country);
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
     * Tests {@link CountryRepository#findAllByCountryStartingWithOrderByCountryAsc(String)}
     * ({@code SAKILA-COUNTRY-B-02}).
     */
    @Nested
    class FindAllByCountryStartingWithOrderByCountryAsc_Test {

        private static Stream<Country> countries() {
            return CountryRepository_SpringBootIT.countries();
        }

        /**
         * Asserts that the prefix of the exercise, {@code S}, finds the eleven countries the exercise's SQL gives, by
         * name.
         */
        @Test
        void __S() {
            final var found = repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc("S");
            assertThat(found)
                    .extracting(Country::getCountryId)
                    .containsExactly(81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91);
            assertThat(found.getFirst().getCountry()).isEqualTo("Saint Vincent and the Grenadines");
        }

        /**
         * Asserts that the first letter of a selected country's name finds that country, among countries whose names
         * start with that letter only.
         *
         * @param country one of the countries selected in {@link CountryRepository_SpringBootIT#__()}.
         */
        @MethodSource("countries")
        @ParameterizedTest
        void __(final Country country) {
            final var prefix = country.getCountry().substring(0, 1);
            final var found = repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc(prefix);
            assertThat(found)
                    .contains(country)
                    .allSatisfy(c -> assertThat(c.getCountry()).startsWithIgnoringCase(prefix));
        }

        /**
         * Asserts that a {@code null} prefix is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPrefix() {
            assertThatThrownBy(() -> repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CountryRepository#listWithoutCities(Pageable)} ({@code SAKILA-COUNTRY-I-01}).
     */
    @Nested
    class ListWithoutCities_Test {

        private static Stream<Country> countries() {
            return CountryRepository_SpringBootIT.countries();
        }

        /**
         * Asserts that a page by id holds countries with no city in {@link CityRepository}, and that they are as many
         * in all as the countries minus those whose cities are not empty; the installed sample data has none, as the
         * exercise's SQL gives, so the page is empty there.
         */
        @Test
        void __() {
            final var page = repositoryInstance().listWithoutCities(PageRequest.of(0, 5, BY_COUNTRY_ID));
            assertPage(page);
            assertThat(page.getContent()).allSatisfy(c -> assertThat(countCitiesOf(c)).isZero());
            final Specification<Country> withCities = (r, q, b) -> b.isNotEmpty(r.get(Country_.cities));
            assertThat(page.getTotalElements())
                    .isZero()
                    .isEqualTo(repositoryInstance().count() - repositoryInstance().count(withCities));
        }

        /**
         * Asserts that a selected country is listed if, and only if, it has no city.
         *
         * @param country one of the countries selected in {@link CountryRepository_SpringBootIT#__()}.
         */
        @MethodSource("countries")
        @ParameterizedTest
        void __(final Country country) {
            final var all = repositoryInstance().listWithoutCities(Pageable.unpaged(BY_COUNTRY_ID));
            if (countCitiesOf(country) == 0L) {
                assertThat(all.getContent()).contains(country);
            } else {
                assertThat(all.getContent()).doesNotContain(country);
            }
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listWithoutCities(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CountryRepository#selectCustomerCounts(Pageable)} ({@code SAKILA-COUNTRY-I-02}).
     */
    @Nested
    class SelectCustomerCounts_Test {

        private static final Sort BY_CUSTOMERS_DESC_THEN_COUNTRY =
                Sort.by(Sort.Direction.DESC, "customers").and(Sort.by("country"));

        private static Stream<Country> countries() {
            return CountryRepository_SpringBootIT.countries();
        }

        /**
         * Asserts that the first five, by {@code customers} descending, then by {@code country}, are the five the
         * exercise's SQL gives, and that the next page goes on from there, as many groups in all as the count query
         * says.
         */
        @Test
        void __TopFive() {
            final var page = repositoryInstance().selectCustomerCounts(
                    PageRequest.of(0, 5, BY_CUSTOMERS_DESC_THEN_COUNTRY));
            assertPage(page);
            assertThat(page.getContent())
                    .extracting(CountryCustomerCount::getCountry, CountryCustomerCount::getCustomers)
                    .containsExactly(
                            tuple("India", 60L),
                            tuple("China", 53L),
                            tuple("United States", 36L),
                            tuple("Japan", 31L),
                            tuple("Mexico", 30L));
            final var all = repositoryInstance().selectCustomerCounts(Pageable.unpaged(BY_CUSTOMERS_DESC_THEN_COUNTRY));
            assertThat(page.getTotalElements()).isEqualTo(all.getContent().size());
            final var next = repositoryInstance().selectCustomerCounts(page.nextPageable());
            assertThat(next.getContent())
                    .extracting(CountryCustomerCount::getCountryId)
                    .containsExactlyElementsOf(all.getContent().subList(5, 10).stream()
                                                       .map(CountryCustomerCount::getCountryId).toList());
        }

        /**
         * Asserts that a selected country, if it has a customer, is among all groups, with as many customers as
         * {@link CustomerRepository} counts there, and is not if it has none.
         *
         * @param country one of the countries selected in {@link CountryRepository_SpringBootIT#__()}.
         */
        @MethodSource("countries")
        @ParameterizedTest
        void __(final Country country) {
            final var all = repositoryInstance().selectCustomerCounts(Pageable.unpaged(BY_CUSTOMERS_DESC_THEN_COUNTRY));
            final var customers = countCustomersIn(country);
            final var rows = all.getContent().stream()
                    .filter(c -> c.getCountryId().equals(country.getCountryId()))
                    .toList();
            if (customers == 0L) {
                assertThat(rows).isEmpty();
                return;
            }
            assertThat(rows).singleElement().satisfies(c -> {
                assertThat(c.getCountry()).isEqualTo(country.getCountry());
                assertThat(c.getCustomers()).isEqualTo(customers);
            });
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCustomerCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CountryRepository#selectHoldingHalfOfCustomers()} ({@code SAKILA-COUNTRY-A-01}).
     */
    @Nested
    class SelectHoldingHalfOfCustomers_Test {

        /**
         * Asserts that the native query gives the nine countries, with their counts, running totals and running
         * percentages, that the exercise's SQL gives, in its order.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectHoldingHalfOfCustomers();
            assertThat(list)
                    .extracting(CountryRunningTotal::getCountry, CountryRunningTotal::getCustomers,
                                CountryRunningTotal::getRunningTotal, r -> r.getRunningPercent().doubleValue())
                    .containsExactly(
                            tuple("India", 60L, 60L, 10.02),
                            tuple("China", 53L, 113L, 18.86),
                            tuple("United States", 36L, 149L, 24.87),
                            tuple("Japan", 31L, 180L, 30.05),
                            tuple("Mexico", 30L, 210L, 35.06),
                            tuple("Brazil", 28L, 238L, 39.73),
                            tuple("Russian Federation", 28L, 266L, 44.41),
                            tuple("Philippines", 20L, 286L, 47.75),
                            tuple("Turkey", 15L, 301L, 50.25));
        }

        /**
         * Asserts that the last running total is at least half of all customers, and the one before it less, and that
         * each running percentage is its running total over all customers.
         */
        @Test
        void __Half() {
            final var list = repositoryInstance().selectHoldingHalfOfCustomers();
            assertThat(list).isNotEmpty();
            final var total = customerRepository.count();
            assertThat(list.getLast().getRunningTotal() * 2).isGreaterThanOrEqualTo(total);
            if (list.size() > 1) {
                assertThat(list.get(list.size() - 2).getRunningTotal() * 2).isLessThan(total);
            }
            assertThat(list).allSatisfy(r -> assertThat(r.getRunningPercent()).isEqualByComparingTo(
                    BigDecimal.valueOf(r.getRunningTotal() * 100)
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)));
        }
    }

    /**
     * Tests {@link CountryRepository#selectRankedTopTenByCustomers()} ({@code SAKILA-COUNTRY-A-02}).
     */
    @Nested
    class SelectRankedTopTenByCustomers_Test {

        /**
         * Asserts that the native query gives the ten countries, with their counts and ranks, that the exercise's SQL
         * gives, in its order; the two tied countries share the sixth rank, and the seventh is skipped.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectRankedTopTenByCustomers();
            assertThat(list)
                    .extracting(CountryCustomerRanking::getCountry, CountryCustomerRanking::getCustomers,
                                CountryCustomerRanking::getRanking)
                    .containsExactly(
                            tuple("India", 60L, 1L),
                            tuple("China", 53L, 2L),
                            tuple("United States", 36L, 3L),
                            tuple("Japan", 31L, 4L),
                            tuple("Mexico", 30L, 5L),
                            tuple("Brazil", 28L, 6L),
                            tuple("Russian Federation", 28L, 6L),
                            tuple("Philippines", 20L, 8L),
                            tuple("Turkey", 15L, 9L),
                            tuple("Indonesia", 14L, 10L));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CustomerRepository customerRepository;
}
