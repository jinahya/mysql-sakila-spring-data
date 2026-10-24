package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Address;
import com.github.jinahya.mysql.sakila.persistence.Address_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link AddressRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few addresses of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * the expected output upstream's {@code address_exercises_*.sql} shows. The base's own tests, such as its
 * {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see AddressRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AddressRepository_SpringBootIT
        extends _Repository_SpringBootIT<AddressRepository, Address, Integer> {

    private static List<Address> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Address_.addressId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_ADDRESS_ID = Sort.by(Sort.Direction.ASC, Address_.ADDRESS_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    AddressRepository_SpringBootIT() {
        super(AddressRepository.class, Address.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five addresses, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method; the result goes into a static field so
     * that the static method sources of the nested classes can reach it. The addresses are loaded outside any
     * transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_ADDRESS_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Address> addresses() {
        return entities.stream();
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
     * Tests {@link AddressRepository#findAllByAddress2IsNullOrderByAddressIdAsc()} ({@code SAKILA_ADDRESS_B_03}).
     */
    @Nested
    class FindAllByAddress2IsNullOrderByAddressIdAsc_Test {

        private static Stream<Address> addresses() {
            return AddressRepository_SpringBootIT.addresses();
        }

        /**
         * Asserts that the addresses found are the four of the exercise's expected output, by id.
         */
        @Test
        void __Exercise() {
            final var found = repositoryInstance().findAllByAddress2IsNullOrderByAddressIdAsc();
            assertThat(found)
                    .extracting(Address::getAddressId, Address::getAddress, Address::getDistrict)
                    .containsExactly(
                            tuple(1, "47 MySakila Drive", "Alberta"),
                            tuple(2, "28 MySQL Boulevard", "QLD"),
                            tuple(3, "23 Workhaven Lane", "Alberta"),
                            tuple(4, "1411 Lillydale Drive", "QLD")
                    );
        }

        /**
         * Asserts that a selected address is found exactly when its {@code address2} is {@code null}.
         *
         * @param address one of the addresses selected in {@link AddressRepository_SpringBootIT#__()}.
         */
        @MethodSource("addresses")
        @ParameterizedTest
        void __(final Address address) {
            final var found = repositoryInstance().findAllByAddress2IsNullOrderByAddressIdAsc();
            assertThat(found).isSortedAccordingTo(Comparator.comparing(Address::getAddressId));
            if (address.getAddress2() == null) {
                assertThat(found).contains(address);
            } else {
                assertThat(found).doesNotContain(address);
            }
        }
    }

    /**
     * Tests {@link AddressRepository#findAllByDistrictOrderByAddressIdAsc(String)} ({@code SAKILA_ADDRESS_B_04}).
     */
    @Nested
    class FindAllByDistrictOrderByAddressIdAsc_Test {

        private static Stream<Address> addresses() {
            return AddressRepository_SpringBootIT.addresses();
        }

        /**
         * Asserts, in one transaction, that the exercise's empty district streams the three addresses of the
         * exercise's expected output, by id.
         */
        @Test
        @Transactional
        void __Exercise() {
            try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc("")) {
                assertThat(found.toList())
                        .extracting(Address::getAddressId, Address::getPostalCode, Address::getPhone)
                        .containsExactly(
                                tuple(30, "58327", "998009777982"),
                                tuple(386, "32716", "934352415130"),
                                tuple(519, "65952", "282667506728")
                        );
            }
        }

        /**
         * Asserts, in one transaction, that the district of a selected address streams addresses of that district
         * only, with the selected one among them, by id as the exercise sorts.
         *
         * @param address one of the addresses selected in {@link AddressRepository_SpringBootIT#__()}.
         */
        @MethodSource("addresses")
        @ParameterizedTest
        @Transactional
        void __Transactional(final Address address) {
            final var district = address.getDistrict();
            try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc(district)) {
                assertThat(found.toList())
                        .contains(address)
                        .allSatisfy(a -> assertThat(a.getDistrict()).isEqualToIgnoringCase(district))
                        .isSortedAccordingTo(Comparator.comparing(Address::getAddressId));
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         *
         * @param address one of the addresses selected in {@link AddressRepository_SpringBootIT#__()}.
         */
        @MethodSource("addresses")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final Address address) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByDistrictOrderByAddressIdAsc(address.getDistrict())) {
                    found.forEach(a -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link AddressRepository#listWithCityAndCountry(Pageable)} ({@code SAKILA_ADDRESS_I_01}).
     */
    @Nested
    class ListWithCityAndCountry_Test {

        private static Stream<Address> addresses() {
            return AddressRepository_SpringBootIT.addresses();
        }

        /**
         * Asserts that the first page of five, by id, holds the five addresses of the exercise's expected output, with
         * their cities and countries, read after the method returned, with no transaction: they were fetched.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().listWithCityAndCountry(PageRequest.of(0, 5, BY_ADDRESS_ID));
            assertThat(page.getContent())
                    .extracting(Address::getAddressId, Address::getAddress, a -> a.getCity().getCity(),
                                a -> a.getCity().getCountry().getCountry())
                    .containsExactly(
                            tuple(1, "47 MySakila Drive", "Lethbridge", "Canada"),
                            tuple(2, "28 MySQL Boulevard", "Woodridge", "Australia"),
                            tuple(3, "23 Workhaven Lane", "Lethbridge", "Canada"),
                            tuple(4, "1411 Lillydale Drive", "Woodridge", "Australia"),
                            tuple(5, "1913 Hanoi Way", "Sasebo", "Japan")
                    );
        }

        /**
         * Asserts that a selected address, one of the first five, is listed on the first page of five, by id, with a
         * city and a country, and that the count query counts every address.
         *
         * @param address one of the addresses selected in {@link AddressRepository_SpringBootIT#__()}.
         */
        @MethodSource("addresses")
        @ParameterizedTest
        void __(final Address address) {
            final var page = repositoryInstance().listWithCityAndCountry(PageRequest.of(0, 5, BY_ADDRESS_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
            assertThat(page.getContent()).filteredOn(a -> a.equals(address)).singleElement().satisfies(a -> {
                assertThat(a.getCity().getCity()).isNotBlank();
                assertThat(a.getCity().getCountry().getCountry()).isNotBlank();
            });
        }

        /**
         * Asserts that pages of the given size, by id, walk the first ten addresses in order, as one page of ten has
         * them.
         *
         * @param size the size of a page.
         */
        @ValueSource(ints = {1, 3})
        @ParameterizedTest
        void __Paged(final int size) {
            final var ten = repositoryInstance().listWithCityAndCountry(PageRequest.of(0, 10, BY_ADDRESS_ID))
                    .getContent();
            final var walked = new ArrayList<Address>();
            var page = repositoryInstance().listWithCityAndCountry(PageRequest.of(0, size, BY_ADDRESS_ID));
            while (walked.size() < ten.size()) {
                walked.addAll(page.getContent());
                page = repositoryInstance().listWithCityAndCountry(page.nextPageable());
            }
            assertThat(walked.subList(0, ten.size())).containsExactlyElementsOf(ten);
        }
    }

    /**
     * Tests {@link AddressRepository#selectDistrictsInMoreCountriesThan(long, Pageable)} ({@code SAKILA_ADDRESS_I_02}).
     */
    @Nested
    class SelectDistrictsInMoreCountriesThan_Test {

        private static final Sort BY_COUNTRIES_DESC_THEN_DISTRICT =
                Sort.by(Sort.Direction.DESC, "countries").and(Sort.by("district"));

        /**
         * Asserts that the exercise's threshold, one, selects the six districts of the exercise's expected output, in
         * its order, six in all.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectDistrictsInMoreCountriesThan(
                    1L, PageRequest.of(0, 10, BY_COUNTRIES_DESC_THEN_DISTRICT));
            assertThat(page.getContent())
                    .extracting(column("district", String.class), column("countries", Long.class))
                    .containsExactly(
                            tuple("Central", 3L),
                            tuple("al-Sharqiya", 2L),
                            tuple("Distrito Federal", 2L),
                            tuple("Ontario", 2L),
                            tuple("Punjab", 2L),
                            tuple("Sucre", 2L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(6L);
        }

        /**
         * Asserts that every district selected is in more countries than the threshold, and that the page is ordered
         * by the count and then by the name, as the database compares names, ignoring case.
         *
         * @param countries the threshold.
         */
        @ValueSource(longs = {0L, 1L, 2L})
        @ParameterizedTest
        void __(final long countries) {
            final var page = repositoryInstance().selectDistrictsInMoreCountriesThan(
                    countries, PageRequest.of(0, 10, BY_COUNTRIES_DESC_THEN_DISTRICT));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .allSatisfy(d -> {
                        assertThat(d.get("countries", Long.class)).isGreaterThan(countries);
                        assertThat(d.get("district", String.class)).isNotEmpty();
                    })
                    .isSortedAccordingTo(
                            Comparator.comparing(column("countries", Long.class)).reversed()
                                    .thenComparing(column("district", String.class), String.CASE_INSENSITIVE_ORDER));
        }
    }

    /**
     * Tests {@link AddressRepository#listUnused(Pageable)} ({@code SAKILA_ADDRESS_A_01}).
     */
    @Nested
    class ListUnused_Test {

        private static Stream<Address> addresses() {
            return AddressRepository_SpringBootIT.addresses();
        }

        /**
         * Asserts that no address is listed, as the exercise's expected output shows: every address of the sample data
         * belongs to a customer, a staff member or a store.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().listUnused(PageRequest.of(0, 5, BY_ADDRESS_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a selected address is not listed, as one of the sample data's.
         *
         * @param address one of the addresses selected in {@link AddressRepository_SpringBootIT#__()}.
         */
        @MethodSource("addresses")
        @ParameterizedTest
        void __(final Address address) {
            final var page = repositoryInstance().listUnused(Pageable.unpaged(BY_ADDRESS_ID));
            assertThat(page.getContent()).doesNotContain(address);
        }
    }

    /**
     * Tests {@link AddressRepository#selectPairsSharingPostalCode(Pageable)} ({@code SAKILA_ADDRESS_A_02}).
     */
    @Nested
    class SelectPairsSharingPostalCode_Test {

        private static final Sort BY_ADDRESS_ID_THEN_OTHER = Sort.by("a1.addressId", "a2.addressId");

        /**
         * Asserts that the pairs are the three of the exercise's expected output, by the two ids.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectPairsSharingPostalCode(
                    PageRequest.of(0, 10, BY_ADDRESS_ID_THEN_OTHER));
            assertThat(page.getContent())
                    .extracting(column("address", Address.class, Address::getAddressId),
                                column("address", Address.class, Address::getAddress),
                                column("otherAddress", Address.class, Address::getAddressId),
                                column("otherAddress", Address.class, Address::getAddress),
                                column("address", Address.class, Address::getPostalCode))
                    .containsExactly(
                            tuple(221, "866 Shivapuri Manor", 595, "1074 Sanaa Parkway", "22474"),
                            tuple(304, "1635 Kuwana Boulevard", 603, "1103 Quilmes Boulevard", "52137"),
                            tuple(313, "1191 Sungai Petani Boulevard", 476, "270 Tambaram Parkway", "9668")
                    );
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        /**
         * Asserts that each pair is of two addresses of the same postal code, the lower id first, and that a page of
         * one walks the pairs in the same order.
         */
        @Test
        void __() {
            final var all = repositoryInstance()
                    .selectPairsSharingPostalCode(Pageable.unpaged(BY_ADDRESS_ID_THEN_OTHER))
                    .getContent();
            assertThat(all).isNotEmpty().allSatisfy(p -> {
                final var address = p.get("address", Address.class);
                final var otherAddress = p.get("otherAddress", Address.class);
                assertThat(address.getAddressId()).isLessThan(otherAddress.getAddressId());
                assertThat(otherAddress.getPostalCode()).isEqualTo(address.getPostalCode());
            });
            var page = repositoryInstance().selectPairsSharingPostalCode(
                    PageRequest.of(0, 1, BY_ADDRESS_ID_THEN_OTHER));
            assertThat(page.getTotalElements()).isEqualTo(all.size());
            for (final var expected : all) {
                assertThat(page.getContent()).singleElement().satisfies(p -> {
                    assertThat(p.get("address", Address.class)).isEqualTo(expected.get("address", Address.class));
                    assertThat(p.get("otherAddress", Address.class))
                            .isEqualTo(expected.get("otherAddress", Address.class));
                });
                if (page.hasNext()) {
                    page = repositoryInstance().selectPairsSharingPostalCode(page.nextPageable());
                }
            }
        }
    }

    /**
     * Tests {@link AddressRepository#selectTopThreeDistrictsOfIndiaChinaAndUnitedStates()}
     * ({@code SAKILA_ADDRESS_A_03}).
     */
    @Nested
    class SelectTopThreeDistrictsOfIndiaChinaAndUnitedStates_Test {

        /**
         * Asserts that the rows are the nine of the exercise's expected output, in its order.
         */
        @Test
        void __Exercise() {
            final var list = repositoryInstance().selectTopThreeDistrictsOfIndiaChinaAndUnitedStates();
            assertThat(list)
                    .extracting(column("country", String.class), column("district", String.class),
                                longColumn("addresses"))
                    .containsExactly(
                            tuple("China", "Shandong", 9L),
                            tuple("China", "Hubei", 5L),
                            tuple("China", "Guangdong", 4L),
                            tuple("India", "West Bengali", 9L),
                            tuple("India", "Uttar Pradesh", 8L),
                            tuple("India", "Maharashtra", 7L),
                            tuple("United States", "California", 9L),
                            tuple("United States", "Texas", 5L),
                            tuple("United States", "Illinois", 4L)
                    );
        }
    }
}
