package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.StoreRepository.StoreCounts;
import com.github.jinahya.mysql.sakila.data.StoreRepository.StoreFilmCounts;
import com.github.jinahya.mysql.sakila.data.StoreRepository.StoreMonthlyRentalShare;
import com.github.jinahya.mysql.sakila.persistence.Customer;
import com.github.jinahya.mysql.sakila.persistence.Customer_;
import com.github.jinahya.mysql.sakila.persistence.Inventory;
import com.github.jinahya.mysql.sakila.persistence.Inventory_;
import com.github.jinahya.mysql.sakila.persistence.Staff;
import com.github.jinahya.mysql.sakila.persistence.Staff_;
import com.github.jinahya.mysql.sakila.persistence.Store;
import com.github.jinahya.mysql.sakila.persistence.Store_;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link StoreRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is the stores of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * counts taken again through the repositories of the other tables. The base's own tests, such as its {@code findById}
 * round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see StoreRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreRepository_SpringBootIT
        extends _Repository_SpringBootIT<StoreRepository, Store, Integer> {

    private static List<Store> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Store_.storeId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_STORE_ID = Sort.by(Sort.Direction.ASC, Store_.STORE_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    StoreRepository_SpringBootIT() {
        super(StoreRepository.class, Store.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five stores, by id, as the fixture for the parameterized tests; the sample data has two.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The stores are loaded outside any transaction, so they are detached, and their managers and addresses are
     * uninitialized proxies.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_STORE_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Store> stores() {
        return entities.stream();
    }

    /**
     * Counts the customers of the store of the specified id, through {@link CustomerRepository}.
     *
     * @param storeId the id of the store.
     * @return the number of customers of the store.
     */
    private long countCustomersOf(final Integer storeId) {
        final Specification<Customer> specification =
                (r, q, b) -> b.equal(r.get(Customer_.store).get(Store_.storeId), storeId);
        return customerRepository.count(specification);
    }

    /**
     * Counts the copies in the inventory of the store of the specified id, through {@link InventoryRepository}.
     *
     * @param storeId the id of the store.
     * @return the number of copies of the store.
     */
    private long countCopiesOf(final Integer storeId) {
        final Specification<Inventory> specification = (r, q, b) -> b.equal(r.get(Inventory_.storeId), storeId);
        return inventoryRepository.count(specification);
    }

    /**
     * Counts the staff members who work at the store of the specified id, through {@link StaffRepository}.
     *
     * @param storeId the id of the store.
     * @return the number of staff members of the store.
     */
    private long countStaffOf(final Integer storeId) {
        final Specification<Staff> specification =
                (r, q, b) -> b.equal(r.get(Staff_.store).get(Store_.storeId), storeId);
        return staffRepository.count(specification);
    }

    /**
     * Reads the whole inventory once, through {@link InventoryRepository}, and maps each store's id to the set of ids
     * of the films it has a copy of.
     * <p>
     * The ids are the read-only basic attributes of {@link Inventory}, so no association is touched.
     *
     * @return the ids of the films of each store, by the id of the store.
     */
    private Map<Integer, Set<Integer>> filmIdsByStoreId() {
        final var map = new HashMap<Integer, Set<Integer>>();
        for (final var inventory : inventoryRepository.findAll()) {
            map.computeIfAbsent(inventory.getStoreId(), k -> new HashSet<>()).add(inventory.getFilmId());
        }
        return map;
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
     * Tests {@link StoreRepository#findAllByOrderByStoreIdAsc()} ({@code SAKILA-STORE-B-01}).
     */
    @Nested
    class FindAllByOrderByStoreIdAsc_Test {

        private static Stream<Store> stores() {
            return StoreRepository_SpringBootIT.stores();
        }

        /**
         * Asserts that the list, by id as the exercise orders, is ordered so, holds as many stores as the table, with
         * a selected one among them, and that the ids of each one's manager and address are readable with no
         * transaction: an uninitialized proxy carries its id.
         *
         * @param store one of the stores selected in {@link StoreRepository_SpringBootIT#__()}.
         */
        @MethodSource("stores")
        @ParameterizedTest
        void __(final Store store) {
            final var found = repositoryInstance().findAllByOrderByStoreIdAsc();
            assertThat(found)
                    .hasSize((int) repositoryInstance().count())
                    .contains(store)
                    .isSortedAccordingTo(Comparator.comparing(Store::getStoreId))
                    .allSatisfy(st -> {
                        assertThat(st.getManagerStaff().getStaffId()).isNotNull();
                        assertThat(st.getAddress().getAddressId()).isNotNull();
                    });
        }

        /**
         * Asserts that the list starts with the rows the exercise shows, on the sample data: the id of each store, of
         * its manager and of its address.
         */
        @Test
        void __Expected() {
            final var found = repositoryInstance().findAllByOrderByStoreIdAsc();
            assertThat(found)
                    .extracting(Store::getStoreId, st -> st.getManagerStaff().getStaffId(),
                                st -> st.getAddress().getAddressId())
                    .startsWith(
                            tuple(1, 1, 1),
                            tuple(2, 2, 2)
                    );
        }
    }

    /**
     * Tests {@link StoreRepository#listWithManagerAndCity(Pageable)} ({@code SAKILA-STORE-I-01}).
     */
    @Nested
    class ListWithManagerAndCity_Test {

        private static Stream<Store> stores() {
            return StoreRepository_SpringBootIT.stores();
        }

        /**
         * Asserts that all stores, by id as the exercise sorts, are ordered so, as many as the table holds, with a
         * selected one among them, and that each one's manager, address and the address's city are already
         * initialized, which only the fetches can have done: the test runs in no transaction, so a lazy association
         * would be an uninitialized proxy here. The manager is then compared with the staff member of that id, found
         * through {@link StaffRepository}.
         *
         * @param store one of the stores selected in {@link StoreRepository_SpringBootIT#__()}.
         */
        @MethodSource("stores")
        @ParameterizedTest
        void __(final Store store) {
            final var all = repositoryInstance().listWithManagerAndCity(Pageable.unpaged(BY_STORE_ID));
            assertThat(all.getTotalElements()).isEqualTo(repositoryInstance().count());
            assertThat(all.getContent())
                    .contains(store)
                    .isSortedAccordingTo(Comparator.comparing(Store::getStoreId))
                    .allSatisfy(st -> {
                        assertThat(Hibernate.isInitialized(st.getManagerStaff())).isTrue();
                        assertThat(Hibernate.isInitialized(st.getAddress())).isTrue();
                        assertThat(Hibernate.isInitialized(st.getAddress().getCity())).isTrue();
                        assertThat(st.getAddress().getCity().getCity()).isNotBlank();
                        final var manager = staffRepository.findById(st.getManagerStaff().getStaffId()).orElseThrow();
                        assertThat(st.getManagerStaff().getFirstName()).isEqualTo(manager.getFirstName());
                        assertThat(st.getManagerStaff().getLastName()).isEqualTo(manager.getLastName());
                    });
        }

        /**
         * Asserts that a page of the given size, by id, is consistent, holds as many in all as the table, and has its
         * associations fetched too.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 2})
        @ParameterizedTest
        void __Paged(final int size) {
            final var page = repositoryInstance().listWithManagerAndCity(PageRequest.of(0, size, BY_STORE_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
            assertThat(page.getContent())
                    .hasSize((int) Math.min(size, page.getTotalElements()))
                    .allSatisfy(st -> {
                        assertThat(Hibernate.isInitialized(st.getManagerStaff())).isTrue();
                        assertThat(Hibernate.isInitialized(st.getAddress().getCity())).isTrue();
                    });
        }
    }

    /**
     * Tests {@link StoreRepository#selectCounts(Pageable)} ({@code SAKILA-STORE-I-02}).
     */
    @Nested
    class SelectCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("storeId");

        /**
         * Asserts that a page of the given size, by the id as the exercise sorts, is ordered so, holds as many stores
         * in all as the table, and that each of the three counts is the count taken again through the repository of
         * that table, by a {@link Specification} on the id of the store.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectCounts(PageRequest.of(0, size, BY_STORE_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(StoreCounts::getStoreId))
                    .allSatisfy(c -> {
                        assertThat(c.getCustomers()).as("customers of store %d", c.getStoreId())
                                .isEqualTo(countCustomersOf(c.getStoreId()));
                        assertThat(c.getCopies()).as("copies of store %d", c.getStoreId())
                                .isEqualTo(countCopiesOf(c.getStoreId()));
                        assertThat(c.getStaff()).as("staff of store %d", c.getStoreId())
                                .isEqualTo(countStaffOf(c.getStoreId()));
                    });
        }

        /**
         * Asserts that all rows sorted by one of the subquery columns, the most customers first, are ordered so; the
         * column is an alias of the select, not a path of the entity, and the sort has to go by the alias.
         */
        @Test
        void __SortedByCustomers() {
            final var page = repositoryInstance().selectCounts(
                    PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "customers")));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparingLong(StoreCounts::getCustomers).reversed());
        }
    }

    /**
     * Tests {@link StoreRepository#selectFilmCounts(Pageable)} ({@code SAKILA-STORE-A-01}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_STORE_ID = Sort.by("storeId");

        /**
         * Asserts that a page of the given size, by the id as the exercise sorts, is ordered so, and that each of the
         * two counts is what the whole inventory, read once through {@link InventoryRepository} and grouped by store
         * in memory, says: the distinct films of the store, and those of them no other store has.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var filmIdsByStoreId = filmIdsByStoreId();
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, size, BY_STORE_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(filmIdsByStoreId.size());
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(StoreFilmCounts::getStoreId))
                    .allSatisfy(c -> {
                        final var films = filmIdsByStoreId.get(c.getStoreId());
                        assertThat(films).as("films of store %d", c.getStoreId()).isNotNull();
                        assertThat(c.getFilms()).isEqualTo(films.size());
                        final var onlyHere = films.stream()
                                .filter(f -> filmIdsByStoreId.entrySet().stream()
                                        .filter(e -> !e.getKey().equals(c.getStoreId()))
                                        .noneMatch(e -> e.getValue().contains(f)))
                                .count();
                        assertThat(c.getFilmsOnlyHere()).as("films only at store %d", c.getStoreId())
                                .isEqualTo(onlyHere);
                        assertThat(c.getFilmsOnlyHere()).isLessThanOrEqualTo(c.getFilms());
                    });
        }
    }

    /**
     * Tests {@link StoreRepository#selectMonthlyRentalShares(Pageable)} ({@code SAKILA-STORE-A-02}).
     */
    @Nested
    class SelectMonthlyRentalShares_Test {

        private static final Sort BY_MONTH_THEN_STORE_ID = Sort.by("rentalYear", "rentalMonth", "storeId");

        private static Stream<Store> stores() {
            return StoreRepository_SpringBootIT.stores();
        }

        /**
         * Asserts that all the rows, by the month and then the store as the exercise sorts, are the rows the exercise
         * shows, on the sample data: the year and the month, the store, its rentals and its share of the month.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectMonthlyRentalShares(Pageable.unpaged(BY_MONTH_THEN_STORE_ID));
            assertThat(page.getContent())
                    .extracting(StoreMonthlyRentalShare::getRentalYear, StoreMonthlyRentalShare::getRentalMonth,
                                StoreMonthlyRentalShare::getStoreId, StoreMonthlyRentalShare::getRentals,
                                m -> m.getPercent().doubleValue())
                    .containsExactly(
                            tuple(2005, 5, 1, 575L, 49.74d),
                            tuple(2005, 5, 2, 581L, 50.26d),
                            tuple(2005, 6, 1, 1121L, 48.51d),
                            tuple(2005, 6, 2, 1190L, 51.49d),
                            tuple(2005, 7, 1, 3334L, 49.69d),
                            tuple(2005, 7, 2, 3375L, 50.31d),
                            tuple(2005, 8, 1, 2801L, 49.26d),
                            tuple(2005, 8, 2, 2885L, 50.74d),
                            tuple(2006, 2, 1, 92L, 50.55d),
                            tuple(2006, 2, 2, 90L, 49.45d)
                    );
            assertThat(page.getTotalElements()).isEqualTo(page.getNumberOfElements());
        }

        /**
         * Asserts that the rentals of every row add up to the number of all rentals, read through
         * {@link RentalRepository}, every rental being of one copy of one store, and that the shares of each month add
         * up to a hundred, give or take the rounding.
         */
        @Test
        void __AddUp() {
            final var all = repositoryInstance().selectMonthlyRentalShares(Pageable.unpaged(BY_MONTH_THEN_STORE_ID));
            final var rentals = all.getContent().stream().mapToLong(StoreMonthlyRentalShare::getRentals).sum();
            assertThat(rentals).isEqualTo(rentalRepository.count());
            final var percents = new HashMap<Integer, BigDecimal>();
            for (final var m : all) {
                percents.merge(m.getRentalYear() * 100 + m.getRentalMonth(), m.getPercent(), BigDecimal::add);
            }
            assertThat(percents.values()).allSatisfy(p -> {
                assertThat(p.doubleValue()).isBetween(99.98d, 100.02d);
            });
        }

        /**
         * Asserts that a selected store has a row in every month the sample data has a rental of its copies in.
         *
         * @param store one of the stores selected in {@link StoreRepository_SpringBootIT#__()}.
         */
        @MethodSource("stores")
        @ParameterizedTest
        void __(final Store store) {
            final var all = repositoryInstance().selectMonthlyRentalShares(Pageable.unpaged(BY_MONTH_THEN_STORE_ID));
            assertThat(all.getContent())
                    .filteredOn(m -> m.getStoreId().equals(store.getStoreId()))
                    .isNotEmpty()
                    .allSatisfy(m -> {
                        assertThat(m.getRentals()).isPositive();
                        assertThat(m.getPercent()).isPositive();
                    });
        }

        /**
         * Asserts that pages of the given size walk the same rows, in the same order, as the unpaged page, and that
         * the count query counts as many rows as there are.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {3, 4})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().selectMonthlyRentalShares(Pageable.unpaged(BY_MONTH_THEN_STORE_ID));
            var page = repositoryInstance().selectMonthlyRentalShares(PageRequest.of(0, size, BY_MONTH_THEN_STORE_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getNumberOfElements());
            final var walked = new ArrayList<String>();
            while (true) {
                assertPage(page);
                page.forEach(m -> walked.add(m.getRentalYear() + "-" + m.getRentalMonth() + "/" + m.getStoreId()));
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().selectMonthlyRentalShares(page.nextPageable());
            }
            assertThat(walked).containsExactlyElementsOf(
                    all.stream().map(m -> m.getRentalYear() + "-" + m.getRentalMonth() + "/" + m.getStoreId())
                            .toList());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private StaffRepository staffRepository;
}
