package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.City;
import com.github.jinahya.mysql.sakila.persistence.City_;
import com.github.jinahya.mysql.sakila.persistence.Country;
import com.github.jinahya.mysql.sakila.persistence.Country_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link CityRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the countries and cities it needs, with the
 * names it needs, through {@link _Repository_DataJpaTest_Fixtures}, and the slice rolls them back when it ends. The
 * names are random, and unique to the test, so a test can count what it finds, whatever else the H2 of the cached
 * context may hold. No customer can be persisted into the H2 (see {@link _Repository_DataJpaTest_Fixtures}), so the
 * exercise on customers runs against an empty {@code customer} table.
 *
 * @see CityRepository_SpringBootIT
 */
class CityRepository_DataJpaTest
        extends _Repository_DataJpaTest<CityRepository, City, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code City_.cityId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_CITY_ID = Sort.by(Sort.Direction.ASC, City_.CITY_ID);

    /**
     * Creates a new instance.
     */
    CityRepository_DataJpaTest() {
        super(CityRepository.class, City.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Saves a randomized country with the specified name.
     *
     * @param name the name of the country.
     * @return the saved country, managed in the test's persistence context.
     */
    private Country saveCountry(final String name) {
        final var country = fixtures().country();
        country.setCountry(name);
        return country;
    }

    /**
     * Saves a randomized city with the specified name, in the specified country, through the repository under test.
     *
     * @param name    the name of the city.
     * @param country the country of the city.
     * @return the saved city, managed in the test's persistence context.
     */
    private City saveCity(final String name, final Country country) {
        final var city = _Repository_DataJpaTest_Fixtures.randomized(City.class);
        city.setCity(name);
        city.setCountry(country);
        return repositoryInstance().save(city);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link CityRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The id is drawn from the whole {@code int} range, so it is all but certain to miss the small identity values
         * the H2 hands out.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves a city, and asserts that finding it by its id returns that very instance, from the test's persistence
         * context.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = saveCity(randomName(), fixtures().country());
            final var found = repositoryInstance().findById(saved.getCityId());
            assertThat(found).hasValueSatisfying(v -> assertThat(v).isSameAs(saved));
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link CityRepository#findAllByCityStartingWithOrderByCityAsc(String)} ({@code SAKILA-CITY-B-01}).
     */
    @Nested
    class FindAllByCityStartingWithOrderByCityAsc_Test {

        /**
         * Asserts that a prefix no city name starts with finds an empty list.
         */
        @Test
        void _Empty_Unknown() {
            final var found = repositoryInstance().findAllByCityStartingWithOrderByCityAsc(randomName());
            assertThat(found).isEmpty();
        }

        /**
         * Saves cities whose names share a prefix, and one whose does not, and asserts that the prefix finds the
         * former, and those very instances.
         */
        @Test
        void __() {
            final var prefix = randomName();
            final var country = fixtures().country();
            final var b = saveCity(prefix + 'b', country);
            final var a = saveCity(prefix + 'a', country);
            final var other = saveCity(randomName(), country);
            final var found = repositoryInstance().findAllByCityStartingWithOrderByCityAsc(prefix);
            assertThat(found)
                    .containsExactlyInAnyOrder(a, b)
                    .doesNotContain(other)
                    .allSatisfy(c -> assertThat(c.getCity()).startsWith(prefix));
            assertThat(found).anySatisfy(c -> assertThat(c).isSameAs(a));
        }

        /**
         * Saves cities whose names share a prefix, in the reverse of their names' order, and asserts that they come
         * back by name, as the query orders them.
         */
        @Test
        void __OrderByCity() {
            final var prefix = randomName();
            final var country = fixtures().country();
            final var c = saveCity(prefix + 'c', country);
            final var b = saveCity(prefix + 'b', country);
            final var a = saveCity(prefix + 'a', country);
            final var found = repositoryInstance().findAllByCityStartingWithOrderByCityAsc(prefix);
            assertThat(found).containsExactly(a, b, c);
        }

        /**
         * Asserts that a {@code null} prefix is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPrefix() {
            assertThatThrownBy(() -> repositoryInstance().findAllByCityStartingWithOrderByCityAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#listByCountry(String, Pageable)} ({@code SAKILA-CITY-I-01}).
     */
    @Nested
    class ListByCountry_Test {

        private static final Sort BY_CITY = Sort.by(City_.CITY);

        /**
         * Asserts that a country name no country has lists an empty page, with no elements in all.
         */
        @Test
        void _Empty_Unknown() {
            final var page = repositoryInstance().listByCountry(randomName(), PageRequest.of(0, 5, BY_CITY));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves three cities of a country, and one of another, and asserts that the country's name lists the three,
         * by name, as the exercise sorts.
         */
        @Test
        void __() {
            final var country = saveCountry(randomName());
            final var c = saveCity("c" + randomName(), country);
            final var a = saveCity("a" + randomName(), country);
            final var b = saveCity("b" + randomName(), country);
            final var other = saveCity("a" + randomName(), fixtures().country());
            final var page = repositoryInstance().listByCountry(country.getCountry(), Pageable.unpaged(BY_CITY));
            assertThat(page.getContent())
                    .containsExactly(a, b, c)
                    .doesNotContain(other);
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        /**
         * Saves three cities of a country, and asserts that pages of two, by name, hold the first two and then the
         * third, three in all.
         */
        @Test
        void __Paged() {
            final var country = saveCountry(randomName());
            final var a = saveCity("a" + randomName(), country);
            final var b = saveCity("b" + randomName(), country);
            final var c = saveCity("c" + randomName(), country);
            final var page0 = repositoryInstance().listByCountry(country.getCountry(), PageRequest.of(0, 2, BY_CITY));
            assertThat(page0.getContent()).containsExactly(a, b);
            assertThat(page0.getTotalElements()).isEqualTo(3L);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            final var page1 = repositoryInstance().listByCountry(country.getCountry(), page0.nextPageable());
            assertThat(page1.getContent()).containsExactly(c);
            assertThat(page1.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} country or a {@code null} pageable is rejected before any query runs: the
         * package is {@code @NullMarked}, so Spring Data checks the arguments, and throws
         * {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().listByCountry(null, Pageable.unpaged()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repositoryInstance().listByCountry(randomName(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#selectCityCounts(Pageable)} ({@code SAKILA-CITY-I-02}).
     */
    @Nested
    class SelectCityCounts_Test {

        private static final Sort BY_CITIES_DESC_THEN_COUNTRY =
                Sort.by(Sort.Direction.DESC, "cities").and(Sort.by("co.country"));

        /**
         * Saves a country with three cities, one with two, one with one, and one with none, and asserts that the
         * three with a city are selected, each the country itself, with their counts, and the one with none is not.
         */
        @Test
        void __() {
            final var three = saveCountry(randomName());
            final var two = saveCountry(randomName());
            final var one = saveCountry(randomName());
            final var none = saveCountry(randomName());
            for (int i = 0; i < 3; i++) {
                saveCity(randomName(), three);
            }
            for (int i = 0; i < 2; i++) {
                saveCity(randomName(), two);
            }
            saveCity(randomName(), one);
            final var all = repositoryInstance().selectCityCounts(Pageable.unpaged(BY_CITIES_DESC_THEN_COUNTRY));
            assertThat(all.getContent())
                    .noneMatch(t -> t.get("country", Country.class).equals(none))
                    .extracting(column("country", Country.class), column("cities", Long.class))
                    .contains(tuple(three, 3L), tuple(two, 2L), tuple(one, 1L));
        }

        /**
         * Saves a country with three cities and two with one each, and asserts that the select is ordered by the count
         * descending, then by the name, as the exercise sorts.
         */
        @Test
        void __Order() {
            final var three = saveCountry("c" + randomName());
            final var oneB = saveCountry("b" + randomName());
            final var oneA = saveCountry("a" + randomName());
            for (int i = 0; i < 3; i++) {
                saveCity(randomName(), three);
            }
            saveCity(randomName(), oneB);
            saveCity(randomName(), oneA);
            final var all = repositoryInstance().selectCityCounts(Pageable.unpaged(BY_CITIES_DESC_THEN_COUNTRY));
            assertThat(all.getContent())
                    .extracting(column("country", Country.class))
                    .containsSubsequence(three, oneA, oneB);
        }

        /**
         * Saves three countries with a city each, and asserts that a page of two holds the first two groups of all, and
         * that the count query counts the groups, three, not the cities.
         */
        @Test
        void __Paged() {
            for (int i = 0; i < 3; i++) {
                saveCity(randomName(), saveCountry(randomName()));
            }
            final var all = repositoryInstance().selectCityCounts(Pageable.unpaged(BY_CITIES_DESC_THEN_COUNTRY));
            final var page0 = repositoryInstance().selectCityCounts(PageRequest.of(0, 2, BY_CITIES_DESC_THEN_COUNTRY));
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isEqualTo(all.getTotalElements()).isEqualTo(3L);
            assertThat(page0.getContent())
                    .extracting(column("country", Country.class))
                    .containsExactlyElementsOf(all.getContent().subList(0, 2).stream()
                                                       .map(column("country", Country.class)).toList());
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCityCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#listOnlyCityOfCountry(Pageable)} ({@code SAKILA-CITY-A-01}).
     */
    @Nested
    class ListOnlyCityOfCountry_Test {

        private static final Sort BY_COUNTRY_THEN_CITY_ID =
                Sort.by(City_.COUNTRY + '.' + Country_.COUNTRY).and(Sort.by(City_.CITY_ID));

        /**
         * Saves a country with one city, and one with two, and asserts that the former's city is listed and neither
         * of the latter's is.
         */
        @Test
        void __() {
            final var only = saveCity(randomName(), saveCountry(randomName()));
            final var two = saveCountry(randomName());
            final var first = saveCity(randomName(), two);
            final var second = saveCity(randomName(), two);
            final var page = repositoryInstance().listOnlyCityOfCountry(Pageable.unpaged(BY_COUNTRY_THEN_CITY_ID));
            assertThat(page.getContent())
                    .contains(only)
                    .doesNotContain(first, second);
        }

        /**
         * Saves three countries with one city each, named so that their order by the country's name is not their
         * order by id, and asserts that they are listed by the country's name, as the exercise sorts.
         */
        @Test
        void __Order() {
            final var prefix = randomName();
            final var c = saveCity(randomName(), saveCountry(prefix + 'c'));
            final var a = saveCity(randomName(), saveCountry(prefix + 'a'));
            final var b = saveCity(randomName(), saveCountry(prefix + 'b'));
            final var page = repositoryInstance().listOnlyCityOfCountry(Pageable.unpaged(BY_COUNTRY_THEN_CITY_ID));
            assertThat(page.getContent()).containsSubsequence(a, b, c);
        }

        /**
         * Saves three countries with one city each, and asserts that a page of one holds one of them, and that the
         * pages are as many as the cities listed in all.
         */
        @Test
        void __Paged() {
            for (int i = 0; i < 3; i++) {
                saveCity(randomName(), saveCountry(randomName()));
            }
            final var all = repositoryInstance().listOnlyCityOfCountry(Pageable.unpaged(BY_COUNTRY_THEN_CITY_ID));
            final var page = repositoryInstance().listOnlyCityOfCountry(PageRequest.of(0, 1, BY_COUNTRY_THEN_CITY_ID));
            assertThat(page.getContent()).containsExactly(all.getContent().getFirst());
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            assertThat(page.getTotalPages()).isEqualTo(all.getContent().size());
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listOnlyCityOfCountry(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CityRepository#listAllCustomersInactive(Pageable)} ({@code SAKILA-CITY-A-02}).
     */
    @Nested
    class ListAllCustomersInactive_Test {

        private static final Sort BY_CITY_THEN_CITY_ID = Sort.by(City_.CITY).and(BY_CITY_ID);

        /**
         * Saves a city with an address but no customer, and asserts that it is not listed: no customer can be saved
         * into the H2, and a city with no customer is not a city of inactive customers.
         */
        @Test
        void _Empty_NoCustomers() {
            final var city = saveCity(randomName(), fixtures().country());
            fixtures().address(city);
            final var page = repositoryInstance().listAllCustomersInactive(PageRequest.of(0, 5, BY_CITY_THEN_CITY_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listAllCustomersInactive(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
