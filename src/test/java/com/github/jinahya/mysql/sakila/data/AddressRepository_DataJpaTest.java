package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.AddressRepository.AddressPair;
import com.github.jinahya.mysql.sakila.data.AddressRepository.CountryDistrictCount;
import com.github.jinahya.mysql.sakila.data.AddressRepository.DistrictCountryCount;
import com.github.jinahya.mysql.sakila.persistence.Address;
import com.github.jinahya.mysql.sakila.persistence.Address_;
import com.github.jinahya.mysql.sakila.persistence.City;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link AddressRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the addresses it needs, each in a city of a
 * country of its own unless the test says otherwise, through {@link #fixtures()}, and sets only the attributes the test
 * is about, with random values; the slice rolls them back when it ends. No customer, staff member or store can be
 * saved into the empty H2 (see {@link _Repository_DataJpaTest_Fixtures}), so every address saved here is unused.
 *
 * @see AddressRepository_SpringBootIT
 */
class AddressRepository_DataJpaTest
        extends _Repository_DataJpaTest<AddressRepository, Address, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Address_.addressId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_ADDRESS_ID = Sort.by(Sort.Direction.ASC, Address_.ADDRESS_ID);

    /**
     * Creates a new instance.
     */
    AddressRepository_DataJpaTest() {
        super(AddressRepository.class, Address.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns a random name of a district: the first twenty characters of a random name, as many as the column holds.
     *
     * @return a random name of a district.
     */
    private static String randomDistrict() {
        return randomName().substring(0, Address.COLUMN_LENGTH_DISTRICT);
    }

    /**
     * Returns a random postal code: the first ten characters of a random name, as many as the column holds.
     *
     * @return a random postal code.
     */
    private static String randomPostalCode() {
        return randomName().substring(0, Address.COLUMN_LENGTH_POSTAL_CODE);
    }

    /**
     * Saves an address, in the specified city, with the specified district, through the repository under test.
     *
     * @param city     the city of the address.
     * @param district the district of the address.
     * @return the saved address.
     */
    private Address saveAddress(final City city, final String district) {
        final var address = fixtures().address(city);
        address.setDistrict(district);
        return repositoryInstance().save(address);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link AddressRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            assertThat(repositoryInstance().findById(id)).isEmpty();
        }

        /**
         * Saves an address, and asserts that finding it by its id returns that very instance, from the test's
         * persistence context.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = fixtures().address();
            assertThat(repositoryInstance().findById(saved.getAddressId())).hasValueSatisfying(v -> {
                assertThat(v).isSameAs(saved);
            });
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link AddressRepository#findAllByAddress2IsNullOrderByAddressIdAsc()} ({@code SAKILA-ADDRESS-B-03}).
     */
    @Nested
    class FindAllByAddress2IsNullOrderByAddressIdAsc_Test {

        /**
         * Saves two addresses with no {@code address2}, and one with an empty one, and asserts that the former two are
         * found, by id as the exercise sorts, and the latter is not.
         */
        @Test
        void __() {
            final var null1 = fixtures().address();
            null1.setAddress2(null);
            final var null2 = fixtures().address();
            null2.setAddress2(null);
            final var empty = fixtures().address();
            empty.setAddress2("");
            final var found = repositoryInstance().findAllByAddress2IsNullOrderByAddressIdAsc();
            assertThat(found)
                    .containsSubsequence(null1, null2)
                    .doesNotContain(empty)
                    .allSatisfy(a -> assertThat(a.getAddress2()).isNull());
        }
    }

    /**
     * Tests {@link AddressRepository#findAllByDistrictOrderByAddressIdAsc(String)} ({@code SAKILA-ADDRESS-B-04}).
     */
    @Nested
    class FindAllByDistrictOrderByAddressIdAsc_Test {

        /**
         * Saves two addresses of one district, and one of another, and asserts that the district streams the two, by id
         * as the exercise sorts.
         */
        @Test
        void __() {
            final var district = randomDistrict();
            final var city = fixtures().city();
            final var address1 = saveAddress(city, district);
            final var address2 = saveAddress(city, district);
            final var other = saveAddress(city, randomDistrict());
            try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc(district)) {
                assertThat(found.toList())
                        .containsExactly(address1, address2)
                        .doesNotContain(other);
            }
        }

        /**
         * Saves an address of an empty district, as the exercise looks for, and asserts that {@code ""} streams it.
         */
        @Test
        void __Empty() {
            final var empty = saveAddress(fixtures().city(), "");
            try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc("")) {
                assertThat(found.toList()).contains(empty).allSatisfy(a -> assertThat(a.getDistrict()).isEmpty());
            }
        }

        /**
         * Asserts that a district no address has streams nothing.
         */
        @Test
        void _Empty_Unknown() {
            try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc(randomDistrict())) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc(randomDistrict())) {
                    found.forEach(a -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} district is refused with {@link IllegalArgumentException}, before any query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().findAllByDistrictOrderByAddressIdAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link AddressRepository#listWithCityAndCountry(Pageable)} ({@code SAKILA-ADDRESS-I-01}).
     */
    @Nested
    class ListWithCityAndCountry_Test {

        /**
         * Saves three addresses, and asserts that the page lists them by id, with their cities and countries, and that
         * the count query, which runs the same specification without its fetch joins, counts them.
         */
        @Test
        void __() {
            final var address1 = fixtures().address();
            final var address2 = fixtures().address();
            final var address3 = fixtures().address();
            final var page = repositoryInstance().listWithCityAndCountry(PageRequest.of(0, 2, BY_ADDRESS_ID));
            assertThat(page.getContent())
                    .extracting(a -> a, a -> a.getCity().getCity(), a -> a.getCity().getCountry().getCountry())
                    .containsExactly(
                            tuple(address1, address1.getCity().getCity(),
                                  address1.getCity().getCountry().getCountry()),
                            tuple(address2, address2.getCity().getCity(),
                                  address2.getCity().getCountry().getCountry())
                    );
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3L);
            final var all = repositoryInstance().listWithCityAndCountry(Pageable.unpaged(BY_ADDRESS_ID));
            assertThat(all.getContent()).containsSubsequence(address1, address2, address3);
        }

        /**
         * Asserts that a {@code null} pageable is refused with {@link IllegalArgumentException}, before any query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().listWithCityAndCountry(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link AddressRepository#selectDistrictsInMoreCountriesThan(long, Pageable)} ({@code SAKILA-ADDRESS-I-02}).
     */
    @Nested
    class SelectDistrictsInMoreCountriesThan_Test {

        private static final Sort BY_COUNTRIES_DESC_THEN_DISTRICT =
                Sort.by(Sort.Direction.DESC, "countries").and(Sort.by("district"));

        /**
         * Saves a district in three countries, one in two, and one in a single country, twice, and asserts that a
         * threshold of one selects the first two, the one in more countries first, two in all, and leaves the third
         * out; and that a threshold of two selects the first alone.
         */
        @Test
        void __() {
            final var three = randomDistrict();
            final var two = randomDistrict();
            final var one = randomDistrict();
            for (int i = 0; i < 3; i++) {
                saveAddress(fixtures().city(), three);
            }
            for (int i = 0; i < 2; i++) {
                saveAddress(fixtures().city(), two);
            }
            final var city = fixtures().city();
            saveAddress(city, one);
            saveAddress(fixtures().city(city.getCountry()), one);
            final var aboveOne = repositoryInstance().selectDistrictsInMoreCountriesThan(
                    1L, PageRequest.of(0, 1, BY_COUNTRIES_DESC_THEN_DISTRICT));
            assertThat(aboveOne.getContent())
                    .extracting(DistrictCountryCount::getDistrict, DistrictCountryCount::getCountries)
                    .containsExactly(tuple(three, 3L));
            assertThat(aboveOne.getTotalElements()).isEqualTo(2L);
            final var next = repositoryInstance().selectDistrictsInMoreCountriesThan(1L, aboveOne.nextPageable());
            assertThat(next.getContent())
                    .extracting(DistrictCountryCount::getDistrict, DistrictCountryCount::getCountries)
                    .containsExactly(tuple(two, 2L));
            final var aboveTwo = repositoryInstance().selectDistrictsInMoreCountriesThan(
                    2L, Pageable.unpaged(BY_COUNTRIES_DESC_THEN_DISTRICT));
            assertThat(aboveTwo.getContent())
                    .extracting(DistrictCountryCount::getDistrict)
                    .containsExactly(three);
        }

        /**
         * Saves addresses of an empty district in two countries, and asserts that the empty district is not selected,
         * as the exercise leaves it out.
         */
        @Test
        void __EmptyDistrict() {
            saveAddress(fixtures().city(), "");
            saveAddress(fixtures().city(), "");
            final var page = repositoryInstance().selectDistrictsInMoreCountriesThan(1L, Pageable.unpaged());
            assertThat(page.getContent()).extracting(DistrictCountryCount::getDistrict).doesNotContain("");
        }
    }

    /**
     * Tests {@link AddressRepository#listUnused(Pageable)} ({@code SAKILA-ADDRESS-A-01}).
     */
    @Nested
    class ListUnused_Test {

        /**
         * Saves two addresses, and asserts that both are listed, by id: nothing in the H2 refers to them.
         * <p>
         * The three {@code NOT EXISTS} subqueries run, on {@code Customer}, {@code Staff} and {@code Store}; that they
         * run on H2, and let an address no one has through, is what the test proves.
         */
        @Test
        void __() {
            final var address1 = fixtures().address();
            final var address2 = fixtures().address();
            final var page = repositoryInstance().listUnused(Pageable.unpaged(BY_ADDRESS_ID));
            assertThat(page.getContent()).containsSubsequence(address1, address2);
        }

        /**
         * Saves three addresses, and asserts that pages of two by id hold two and then one, at least three in all.
         */
        @Test
        void __Paged() {
            for (int i = 0; i < 3; i++) {
                fixtures().address();
            }
            final var page0 = repositoryInstance().listUnused(PageRequest.of(0, 2, BY_ADDRESS_ID));
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isGreaterThanOrEqualTo(3L);
        }
    }

    /**
     * Tests {@link AddressRepository#selectPairsSharingPostalCode(Pageable)} ({@code SAKILA-ADDRESS-A-02}).
     */
    @Nested
    class SelectPairsSharingPostalCode_Test {

        private static final Sort BY_ADDRESS_ID_THEN_OTHER = Sort.by("addressId", "otherAddressId");

        /**
         * Saves three addresses of one postal code, and one of another, and asserts that the three make three pairs,
         * each once, the lower id first, by the two ids, and that the fourth is in none.
         */
        @Test
        void __() {
            final var postalCode = randomPostalCode();
            final var addresses = new Address[3];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = fixtures().address();
                addresses[i].setPostalCode(postalCode);
            }
            final var other = fixtures().address();
            other.setPostalCode(randomPostalCode());
            final var page = repositoryInstance().selectPairsSharingPostalCode(
                    Pageable.unpaged(BY_ADDRESS_ID_THEN_OTHER));
            assertThat(page.getContent())
                    .filteredOn(p -> postalCode.equals(p.getPostalCode()))
                    .extracting(AddressPair::getAddressId, AddressPair::getOtherAddressId)
                    .containsExactly(
                            tuple(addresses[0].getAddressId(), addresses[1].getAddressId()),
                            tuple(addresses[0].getAddressId(), addresses[2].getAddressId()),
                            tuple(addresses[1].getAddressId(), addresses[2].getAddressId())
                    );
            assertThat(page.getContent())
                    .noneMatch(p -> p.getAddressId().equals(other.getAddressId())
                                    || p.getOtherAddressId().equals(other.getAddressId()));
        }

        /**
         * Saves two addresses with an empty postal code, and asserts that they make no pair, as the exercise leaves
         * the empty code out.
         */
        @Test
        void __EmptyPostalCode() {
            final var address1 = fixtures().address();
            address1.setPostalCode("");
            final var address2 = fixtures().address();
            address2.setPostalCode("");
            final var page = repositoryInstance().selectPairsSharingPostalCode(Pageable.unpaged());
            assertThat(page.getContent()).extracting(AddressPair::getPostalCode).doesNotContain("");
        }

        /**
         * Saves three addresses of one postal code, and asserts that pages of two split the three pairs two and one,
         * three in all, which the count query says.
         */
        @Test
        void __Paged() {
            final var postalCode = randomPostalCode();
            for (int i = 0; i < 3; i++) {
                fixtures().address().setPostalCode(postalCode);
            }
            final var page0 = repositoryInstance().selectPairsSharingPostalCode(
                    PageRequest.of(0, 2, BY_ADDRESS_ID_THEN_OTHER));
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isGreaterThanOrEqualTo(3L);
        }
    }

    /**
     * Tests {@link AddressRepository#selectTopThreeDistrictsOfIndiaChinaAndUnitedStates()}
     * ({@code SAKILA-ADDRESS-A-03}).
     */
    @Nested
    class SelectTopThreeDistrictsOfIndiaChinaAndUnitedStates_Test {

        /**
         * Saves, in a country named {@code India}, two addresses of one district and one of each of three others, and
         * asserts that the native query keeps three: the one of two addresses first, then two of the others by name.
         * <p>
         * The SQL is not MySQL's own: H2 has {@code ROW_NUMBER() OVER (PARTITION BY ...)} too.
         */
        @Test
        void __() {
            final var country = fixtures().country();
            country.setCountry("India");
            final var city = fixtures().city(country);
            final var prefix = randomDistrict().substring(0, 10);
            saveAddress(city, prefix + "b");
            saveAddress(city, prefix + "b");
            saveAddress(city, prefix + "a");
            saveAddress(city, prefix + "c");
            saveAddress(city, prefix + "d");
            final var list = repositoryInstance().selectTopThreeDistrictsOfIndiaChinaAndUnitedStates();
            assertThat(list)
                    .filteredOn(c -> c.getDistrict().startsWith(prefix))
                    .extracting(CountryDistrictCount::getCountry, CountryDistrictCount::getDistrict,
                                CountryDistrictCount::getAddresses)
                    .containsExactly(
                            tuple("India", prefix + "b", 2L),
                            tuple("India", prefix + "a", 1L),
                            tuple("India", prefix + "c", 1L)
                    );
        }
    }
}
