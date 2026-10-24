package com.github.jinahya.mysql.sakila.data;

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
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link CityRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few cities of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * the rows the exercise's SQL gives on the installed data, or against counts taken again through the repositories of
 * the other tables. A city's country is lazy, so a test that reads it runs in a transaction, which is rolled back. The
 * base's own tests, such as its {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see CityRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CityRepository_SpringBootIT
        extends _Repository_SpringBootIT<CityRepository, City, Integer> {

    private static List<City> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code City_.cityId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_CITY_ID = Sort.by(Sort.Direction.ASC, City_.CITY_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    CityRepository_SpringBootIT() {
        super(CityRepository.class, City.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five cities, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The cities are loaded outside any transaction, so they are detached, their countries not loaded.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_CITY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<City> cities() {
        return entities.stream();
    }

    /**
     * Counts the cities in the same country as the specified city, the city itself included, through a
     * {@link Specification}.
     *
     * @param city the city, managed.
     * @return the number of cities in {@code city}'s country.
     */
    private long countCitiesOfCountryOf(final City city) {
        final Specification<City> specification = (r, q, b) -> b.equal(r.get(City_.country), city.getCountry());
        return repositoryInstance().count(specification);
    }

    /**
     * Counts the customers whose address is in the specified city, the active ones only if so specified, through
     * {@link CustomerRepository}.
     *
     * @param city       the city.
     * @param activeOnly {@code true} to count the active customers only.
     * @return the number of customers in {@code city}.
     */
    private long countCustomersIn(final City city, final boolean activeOnly) {
        final Specification<Customer> specification = (r, q, b) -> {
            final var in = b.equal(r.get(Customer_.address).get(Address_.city), city);
            return activeOnly ? b.and(in, b.isTrue(r.get(Customer_.active))) : in;
        };
        return customerRepository.count(specification);
    }

    /**
     * Returns the specified name with its accents stripped, as the case- and accent-insensitive collation of the
     * Sakila database compares it.
     *
     * @param name the name.
     * @return {@code name} with its combining marks removed.
     */
    private static String stripAccents(final String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
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
     * Tests {@link CityRepository#findAllByCityStartingWithOrderByCityAsc(String)} ({@code SAKILA_CITY_B_01}).
     */
    @Nested
    class FindAllByCityStartingWithOrderByCityAsc_Test {

        private static Stream<City> cities() {
            return CityRepository_SpringBootIT.cities();
        }

        /**
         * Asserts that the prefix of the exercise, {@code Q}, finds the four cities the exercise's SQL gives, by name.
         */
        @Test
        void __Q() {
            final var found = repositoryInstance().findAllByCityStartingWithOrderByCityAsc("Q");
            assertThat(found)
                    .extracting(City::getCityId, City::getCity)
                    .containsExactly(
                            tuple(421, "Qalyub"),
                            tuple(422, "Qinhuangdao"),
                            tuple(423, "Qomsheh"),
                            tuple(424, "Quilmes"));
        }

        /**
         * Asserts that the first letter of a selected city's name finds that city, among cities whose names start with
         * that letter only.
         * <p>
         * The column's collation ignores case and accents, so {@code A} finds {@code Águas Lindas de Goiás} too; the
         * names are compared with their accents stripped, and ignoring case.
         *
         * @param city one of the cities selected in {@link CityRepository_SpringBootIT#__()}.
         */
        @MethodSource("cities")
        @ParameterizedTest
        void __(final City city) {
            final var prefix = city.getCity().substring(0, 1);
            final var found = repositoryInstance().findAllByCityStartingWithOrderByCityAsc(prefix);
            assertThat(found)
                    .contains(city)
                    .allSatisfy(c -> assertThat(stripAccents(c.getCity()))
                            .startsWithIgnoringCase(stripAccents(prefix)));
        }

        /**
         * Asserts that a {@code null} prefix is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPrefix() {
            assertThatThrownBy(() -> repositoryInstance().findAllByCityStartingWithOrderByCityAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#listByCountry(String, Pageable)} ({@code SAKILA_CITY_I_01}).
     */
    @Nested
    class ListByCountry_Test {

        private static final Sort BY_CITY = Sort.by(City_.CITY);

        private static Stream<City> cities() {
            return CityRepository_SpringBootIT.cities();
        }

        /**
         * Asserts that the country of the exercise, {@code Canada}, lists the seven cities the exercise's SQL gives,
         * by name, five on the first page and two on the next.
         */
        @Test
        void __Canada() {
            final var page0 = repositoryInstance().listByCountry("Canada", PageRequest.of(0, 5, BY_CITY));
            assertPage(page0);
            assertThat(page0.getContent())
                    .extracting(City::getCityId)
                    .containsExactly(179, 196, 300, 313, 383);
            assertThat(page0.getTotalElements()).isEqualTo(7L);
            final var page1 = repositoryInstance().listByCountry("Canada", page0.nextPageable());
            assertThat(page1.getContent())
                    .extracting(City::getCityId, City::getCity)
                    .containsExactly(tuple(430, "Richmond Hill"), tuple(565, "Vancouver"));
            assertThat(page1.hasNext()).isFalse();
        }

        /**
         * Asserts, in one transaction, that the name of a selected city's country lists that city, among cities of
         * that country only, as many as a {@link Specification} on the country counts.
         *
         * @param city one of the cities selected in {@link CityRepository_SpringBootIT#__()}.
         */
        @Transactional
        @MethodSource("cities")
        @ParameterizedTest
        void __(final City city) {
            final var managed = repositoryInstance().findById(city.getCityId()).orElseThrow();
            final var country = managed.getCountry();
            final var page = repositoryInstance().listByCountry(country.getCountry(), Pageable.unpaged(BY_CITY));
            assertThat(page.getContent())
                    .contains(managed)
                    .allSatisfy(c -> assertThat(c.getCountry()).isSameAs(country));
            assertThat(page.getTotalElements()).isEqualTo(countCitiesOfCountryOf(managed));
        }

        /**
         * Asserts that a {@code null} country or a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().listByCountry(null, Pageable.unpaged()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repositoryInstance().listByCountry("Canada", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#selectCityCounts(Pageable)} ({@code SAKILA_CITY_I_02}).
     */
    @Nested
    class SelectCityCounts_Test {

        private static final Sort BY_CITIES_DESC_THEN_COUNTRY =
                Sort.by(Sort.Direction.DESC, "cities").and(Sort.by("co.country"));

        private static Stream<City> cities() {
            return CityRepository_SpringBootIT.cities();
        }

        /**
         * Asserts that the first five, by {@code cities} descending, then by {@code co.country}, are the five the
         * exercise's SQL gives, and that the groups in all are the countries that have a city.
         */
        @Test
        void __TopFive() {
            final var page = repositoryInstance().selectCityCounts(PageRequest.of(0, 5, BY_CITIES_DESC_THEN_COUNTRY));
            assertPage(page);
            assertThat(page.getContent())
                    .extracting(column("country", Country.class, Country::getCountry), column("cities", Long.class))
                    .containsExactly(
                            tuple("India", 60L),
                            tuple("China", 53L),
                            tuple("United States", 35L),
                            tuple("Japan", 31L),
                            tuple("Mexico", 30L));
            final Specification<Country> withCities = (r, q, b) -> b.isNotEmpty(r.get(Country_.cities));
            assertThat(page.getTotalElements()).isEqualTo(countryRepository.count(withCities));
        }

        /**
         * Asserts, in one transaction, that a selected city's country, the country itself, is among all groups, with as
         * many cities as a {@link Specification} on the country counts.
         *
         * @param city one of the cities selected in {@link CityRepository_SpringBootIT#__()}.
         */
        @Transactional
        @MethodSource("cities")
        @ParameterizedTest
        void __(final City city) {
            final var managed = repositoryInstance().findById(city.getCityId()).orElseThrow();
            final var all = repositoryInstance().selectCityCounts(Pageable.unpaged(BY_CITIES_DESC_THEN_COUNTRY));
            assertThat(all.getContent())
                    .extracting(column("country", Country.class), column("cities", Long.class))
                    .contains(tuple(managed.getCountry(), countCitiesOfCountryOf(managed)));
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCityCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#listOnlyCityOfCountry(Pageable)} ({@code SAKILA_CITY_A_01}).
     */
    @Nested
    class ListOnlyCityOfCountry_Test {

        private static final Sort BY_COUNTRY_THEN_CITY_ID =
                Sort.by(City_.COUNTRY + '.' + Country_.COUNTRY).and(BY_CITY_ID);

        private static Stream<City> cities() {
            return CityRepository_SpringBootIT.cities();
        }

        /**
         * Asserts that the first ten, by the country's name, then by id, are the ten the exercise's SQL gives, and
         * that the cities in all are as many as the countries with exactly one city, which {@code SAKILA_COUNTRY_I_03}
         * counts, forty-two.
         */
        @Test
        void __FirstTen() {
            final var page = repositoryInstance().listOnlyCityOfCountry(PageRequest.of(0, 10, BY_COUNTRY_THEN_CITY_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .extracting(City::getCityId)
                    .containsExactly(251, 516, 493, 586, 576, 14, 53, 363, 378, 530);
            assertThat(page.getTotalElements()).isEqualTo(42L);
        }

        /**
         * Asserts, in one transaction, that a selected city is listed if, and only if, it is the only city of its
         * country, as a {@link Specification} on the country counts.
         *
         * @param city one of the cities selected in {@link CityRepository_SpringBootIT#__()}.
         */
        @Transactional
        @MethodSource("cities")
        @ParameterizedTest
        void __(final City city) {
            final var managed = repositoryInstance().findById(city.getCityId()).orElseThrow();
            final var all = repositoryInstance().listOnlyCityOfCountry(Pageable.unpaged(BY_COUNTRY_THEN_CITY_ID));
            if (countCitiesOfCountryOf(managed) == 1L) {
                assertThat(all.getContent()).contains(managed);
            } else {
                assertThat(all.getContent()).doesNotContain(managed);
            }
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listOnlyCityOfCountry(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#listAllCustomersInactive(Pageable)} ({@code SAKILA_CITY_A_02}).
     */
    @Nested
    class ListAllCustomersInactive_Test {

        private static final Sort BY_CITY_THEN_CITY_ID = Sort.by(City_.CITY).and(BY_CITY_ID);

        private static Stream<City> cities() {
            return CityRepository_SpringBootIT.cities();
        }

        /**
         * Asserts that the cities, by name, then by id, are the fifteen the exercise's SQL gives, in its order, which
         * is the database's collation of the names, and that pages of five walk them.
         */
        @Test
        void __() {
            final var all = repositoryInstance().listAllCustomersInactive(Pageable.unpaged(BY_CITY_THEN_CITY_ID));
            assertThat(all.getContent())
                    .extracting(City::getCityId)
                    .containsExactly(24, 57, 111, 125, 139, 259, 283, 281, 356, 407, 495, 512, 554, 577, 578);
            final var page0 = repositoryInstance().listAllCustomersInactive(PageRequest.of(0, 5, BY_CITY_THEN_CITY_ID));
            assertPage(page0);
            assertThat(page0.getContent()).containsExactlyElementsOf(all.getContent().subList(0, 5));
            assertThat(page0.getTotalElements()).isEqualTo(15L);
            assertThat(page0.getTotalPages()).isEqualTo(3);
        }

        /**
         * Asserts that a selected city is listed if, and only if, it has a customer and no active one, as counted
         * again through {@link CustomerRepository}.
         *
         * @param city one of the cities selected in {@link CityRepository_SpringBootIT#__()}.
         */
        @MethodSource("cities")
        @ParameterizedTest
        void __(final City city) {
            final var all = repositoryInstance().listAllCustomersInactive(Pageable.unpaged(BY_CITY_THEN_CITY_ID));
            final var expected = countCustomersIn(city, false) > 0L && countCustomersIn(city, true) == 0L;
            if (expected) {
                assertThat(all.getContent()).contains(city);
            } else {
                assertThat(all.getContent()).doesNotContain(city);
            }
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listAllCustomersInactive(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CustomerRepository customerRepository;
}
