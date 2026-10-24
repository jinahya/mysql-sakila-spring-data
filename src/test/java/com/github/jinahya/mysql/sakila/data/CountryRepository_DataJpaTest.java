package com.github.jinahya.mysql.sakila.data;

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

/**
 * Tests {@link CountryRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the countries, and cities, it needs, with the
 * names it needs, through the repository under test and {@link _Repository_DataJpaTest_Fixtures}, and the slice rolls
 * them back when it ends. The names are random, and unique to the test, so a test can count what it finds, whatever
 * else the H2 of the cached context may hold. No customer can be persisted into the H2 (see
 * {@link _Repository_DataJpaTest_Fixtures}), so the exercises on customers run against an empty {@code customer}
 * table; that still proves their queries, the native ones included, valid and runnable on the H2.
 *
 * @see CountryRepository_SpringBootIT
 */
class CountryRepository_DataJpaTest
        extends _Repository_DataJpaTest<CountryRepository, Country, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Country_.countryId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_COUNTRY_ID = Sort.by(Sort.Direction.ASC, Country_.COUNTRY_ID);

    /**
     * Creates a new instance.
     */
    CountryRepository_DataJpaTest() {
        super(CountryRepository.class, Country.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Saves a randomized country with the specified name, through the repository under test.
     *
     * @param name the name of the country.
     * @return the saved country, managed in the test's persistence context.
     */
    private Country saveCountry(final String name) {
        final var country = _Repository_DataJpaTest_Fixtures.randomized(Country.class);
        country.setCountry(name);
        return repositoryInstance().save(country);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link CountryRepository#findById(Object)}.
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
         * Saves a country, and asserts that finding it by its id returns that very instance, from the test's
         * persistence context.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = saveCountry(randomName());
            final var found = repositoryInstance().findById(saved.getCountryId());
            assertThat(found).hasValueSatisfying(v -> assertThat(v).isSameAs(saved));
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link CountryRepository#findAllByCountryStartingWithOrderByCountryAsc(String)}
     * ({@code SAKILA_COUNTRY_B_02}).
     */
    @Nested
    class FindAllByCountryStartingWithOrderByCountryAsc_Test {

        /**
         * Asserts that a prefix no country name starts with finds an empty list.
         */
        @Test
        void _Empty_Unknown() {
            final var found = repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc(randomName());
            assertThat(found).isEmpty();
        }

        /**
         * Saves countries whose names share a prefix, and one whose does not, and asserts that the prefix finds the
         * former, and those very instances.
         */
        @Test
        void __() {
            final var prefix = randomName();
            final var b = saveCountry(prefix + 'b');
            final var a = saveCountry(prefix + 'a');
            final var other = saveCountry(randomName());
            final var found = repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc(prefix);
            assertThat(found)
                    .containsExactlyInAnyOrder(a, b)
                    .doesNotContain(other)
                    .allSatisfy(c -> assertThat(c.getCountry()).startsWith(prefix));
            assertThat(found).anySatisfy(c -> assertThat(c).isSameAs(a));
        }

        /**
         * Saves countries whose names share a prefix, in the reverse of their names' order, and asserts that they come
         * back by name, as the query orders them.
         */
        @Test
        void __OrderByCountry() {
            final var prefix = randomName();
            final var c = saveCountry(prefix + 'c');
            final var b = saveCountry(prefix + 'b');
            final var a = saveCountry(prefix + 'a');
            final var found = repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc(prefix);
            assertThat(found).containsExactly(a, b, c);
        }

        /**
         * Asserts that a {@code null} prefix is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPrefix() {
            assertThatThrownBy(() -> repositoryInstance().findAllByCountryStartingWithOrderByCountryAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CountryRepository#listWithoutCities(Pageable)} ({@code SAKILA_COUNTRY_I_01}).
     */
    @Nested
    class ListWithoutCities_Test {

        /**
         * Saves a country with no city and one with a city, and asserts that the former is listed and the latter is
         * not.
         */
        @Test
        void __() {
            final var without = saveCountry(randomName());
            final var with = saveCountry(randomName());
            fixtures().city(with);
            final var page = repositoryInstance().listWithoutCities(Pageable.unpaged(BY_COUNTRY_ID));
            assertThat(page.getContent())
                    .contains(without)
                    .doesNotContain(with);
        }

        /**
         * Saves three countries with no city, and asserts that they are listed by id, in the order they were saved.
         */
        @Test
        void __Order() {
            final var first = saveCountry(randomName());
            final var second = saveCountry(randomName());
            final var third = saveCountry(randomName());
            final var page = repositoryInstance().listWithoutCities(Pageable.unpaged(BY_COUNTRY_ID));
            assertThat(page.getContent()).containsSubsequence(first, second, third);
        }

        /**
         * Saves three countries with no city, and asserts that pages of two by id walk all of them, as many as the
         * count query says.
         */
        @Test
        void __Paged() {
            for (int i = 0; i < 3; i++) {
                saveCountry(randomName());
            }
            final var all = repositoryInstance().listWithoutCities(Pageable.unpaged(BY_COUNTRY_ID));
            final var page0 = repositoryInstance().listWithoutCities(PageRequest.of(0, 2, BY_COUNTRY_ID));
            assertThat(page0.getContent()).containsExactlyElementsOf(all.getContent().subList(0, 2));
            assertThat(page0.getTotalElements()).isEqualTo(all.getTotalElements());
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listWithoutCities(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CountryRepository#selectCustomerCounts(Pageable)} ({@code SAKILA_COUNTRY_I_02}).
     */
    @Nested
    class SelectCustomerCounts_Test {

        /**
         * Saves a country with a city and an address, but no customer, which the H2 cannot hold, and asserts that the
         * joined and grouped select, in the query's own order, runs and selects an empty page, with no countries in
         * all.
         */
        @Test
        void _Empty_NoCustomers() {
            fixtures().address(fixtures().city(saveCountry(randomName())));
            final var page = repositoryInstance().selectCustomerCounts(PageRequest.of(0, 5));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs: the package is
         * {@code @NullMarked}, so Spring Data checks the arguments, and throws {@link IllegalArgumentException}.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCustomerCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CountryRepository#selectHoldingHalfOfCustomers()} ({@code SAKILA_COUNTRY_A_01}).
     */
    @Nested
    class SelectHoldingHalfOfCustomers_Test {

        /**
         * Asserts that the native query, a common table expression with window sums, runs on the H2 too, and selects
         * nothing from the empty {@code customer} table.
         */
        @Test
        void _Empty_NoCustomers() {
            final var list = repositoryInstance().selectHoldingHalfOfCustomers();
            assertThat(list).isEmpty();
        }
    }

    /**
     * Tests {@link CountryRepository#selectRankedTopTenByCustomers()} ({@code SAKILA_COUNTRY_A_02}).
     */
    @Nested
    class SelectRankedTopTenByCustomers_Test {

        /**
         * Asserts that the native query, a {@code RANK()} over the grouped counts, runs on the H2 too, and selects
         * nothing from the empty {@code customer} table.
         */
        @Test
        void _Empty_NoCustomers() {
            final var list = repositoryInstance().selectRankedTopTenByCustomers();
            assertThat(list).isEmpty();
        }
    }
}
