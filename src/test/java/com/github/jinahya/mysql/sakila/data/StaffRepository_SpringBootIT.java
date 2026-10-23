package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.StaffRepository.StaffBestDay;
import com.github.jinahya.mysql.sakila.data.StaffRepository.StaffRentalCount;
import com.github.jinahya.mysql.sakila.persistence.Rental;
import com.github.jinahya.mysql.sakila.persistence.Rental_;
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link StaffRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is the staff members of
 * the sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or
 * against counts taken again through the repositories of the other tables. The base's own tests, such as its
 * {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see StaffRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffRepository_SpringBootIT
        extends _Repository_SpringBootIT<StaffRepository, Staff, Integer> {

    private static List<Staff> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Staff_.staffId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_STAFF_ID = Sort.by(Sort.Direction.ASC, Staff_.STAFF_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    StaffRepository_SpringBootIT() {
        super(StaffRepository.class, Staff.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five staff members, by id, as the fixture for the parameterized tests; the sample data has
     * two.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The staff members are loaded outside any transaction, so they are detached, and their stores are uninitialized
     * proxies.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_STAFF_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Staff> staff() {
        return entities.stream();
    }

    /**
     * Counts the rentals handled by the staff member of the specified id, through {@link RentalRepository}.
     * <p>
     * The staff member has no collection of rentals upstream, so the rentals are read by a {@link Specification}, on
     * the id of the staff member so that no staff instance is needed.
     *
     * @param staffId the id of the staff member.
     * @return the number of rentals the staff member handled.
     */
    private long countRentalsOf(final Integer staffId) {
        final Specification<Rental> specification =
                (r, q, b) -> b.equal(r.get(Rental_.staff).get(Staff_.staffId), staffId);
        return rentalRepository.count(specification);
    }

    /**
     * Lists the stores the specified staff member manages, through {@link StoreRepository}.
     *
     * @param staff the staff member.
     * @return the stores whose manager is {@code staff}; one at most, since the column is unique.
     */
    private List<Store> storesManagedBy(final Staff staff) {
        final Specification<Store> specification = (r, q, b) -> b.equal(r.get(Store_.managerStaff), staff);
        return storeRepository.findAll(specification);
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
     * Tests {@link StaffRepository#findAllByOrderByStaffIdAsc()} ({@code SAKILA-STAFF-B-01}).
     */
    @Nested
    class FindAllByOrderByStaffIdAsc_Test {

        private static Stream<Staff> staff() {
            return StaffRepository_SpringBootIT.staff();
        }

        /**
         * Asserts that the list, by id as the exercise orders, is ordered so, holds as many staff members as the
         * table, with a selected one among them, and that each one's store is already initialized, which only the
         * entity graph can have done: the test runs in no transaction, so a lazy store would be an uninitialized proxy
         * here.
         *
         * @param staff one of the staff members selected in {@link StaffRepository_SpringBootIT#__()}.
         */
        @MethodSource("staff")
        @ParameterizedTest
        void __(final Staff staff) {
            final var found = repositoryInstance().findAllByOrderByStaffIdAsc();
            assertThat(found)
                    .hasSize((int) repositoryInstance().count())
                    .contains(staff)
                    .isSortedAccordingTo(Comparator.comparing(Staff::getStaffId))
                    .allSatisfy(s -> {
                        assertThat(Hibernate.isInitialized(s.getStore())).as("store of staff %d", s.getStaffId())
                                .isTrue();
                        assertThat(s.getActive()).isNotNull();
                    });
        }

        /**
         * Asserts that the list starts with the rows the exercise shows, on the sample data: the id, the names, the id
         * of the store and whether the staff member is active.
         */
        @Test
        void __Expected() {
            final var found = repositoryInstance().findAllByOrderByStaffIdAsc();
            assertThat(found)
                    .extracting(Staff::getStaffId, Staff::getFirstName, Staff::getLastName,
                                s -> s.getStore().getStoreId(), Staff::getActive)
                    .startsWith(
                            tuple(1, "Mike", "Hillyer", 1, true),
                            tuple(2, "Jon", "Stephens", 2, true)
                    );
        }
    }

    /**
     * Tests {@link StaffRepository#findAllByPasswordIsNullOrderByStaffIdAsc()} ({@code SAKILA-STAFF-B-02}).
     */
    @Nested
    class FindAllByPasswordIsNullOrderByStaffIdAsc_Test {

        private static Stream<Staff> staff() {
            return StaffRepository_SpringBootIT.staff();
        }

        /**
         * Asserts, in one transaction, which the stream needs, that the staff members streamed have no password, by
         * id as the exercise orders, and that a selected staff member is streamed exactly when the staff member has
         * no password.
         *
         * @param staff one of the staff members selected in {@link StaffRepository_SpringBootIT#__()}.
         */
        @MethodSource("staff")
        @ParameterizedTest
        @Transactional
        void __Transactional(final Staff staff) {
            try (var found = repositoryInstance().findAllByPasswordIsNullOrderByStaffIdAsc()) {
                final var list = found.toList();
                assertThat(list)
                        .isSortedAccordingTo(Comparator.comparing(Staff::getStaffId))
                        .allSatisfy(s -> assertThat(s.getPassword()).isNull());
                if (staff.getPassword() == null) {
                    assertThat(list).contains(staff);
                } else {
                    assertThat(list).doesNotContain(staff);
                }
            }
        }

        /**
         * Asserts, in one transaction, that the stream yields the row the exercise shows, on the sample data: the
         * staff member {@code 2}, {@code Jon Stephens}, whose username is {@code Jon}.
         */
        @Test
        @Transactional
        void __Expected() {
            try (var found = repositoryInstance().findAllByPasswordIsNullOrderByStaffIdAsc()) {
                assertThat(found)
                        .extracting(Staff::getStaffId, Staff::getFirstName, Staff::getLastName, Staff::getUsername)
                        .containsExactly(tuple(2, "Jon", "Stephens", "Jon"));
            }
        }

        /**
         * Runs the lookup with no transaction, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}.
         */
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByPasswordIsNullOrderByStaffIdAsc()) {
                    found.forEach(s -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link StaffRepository#selectRentalCounts(Pageable)} ({@code SAKILA-STAFF-I-01}).
     */
    @Nested
    class SelectRentalCounts_Test {

        private static final Sort BY_STAFF_ID = Sort.by("staffId");

        /**
         * Asserts that a page of the given size, by the id as the exercise sorts, is ordered so, that the first name
         * is the staff member's, and that each count is the number of rentals the staff member handled, counted again
         * through {@link RentalRepository}.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectRentalCounts(PageRequest.of(0, size, BY_STAFF_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(StaffRentalCount::getStaffId))
                    .allSatisfy(c -> {
                        final var staff = repositoryInstance().findById(c.getStaffId()).orElseThrow();
                        assertThat(staff.getFirstName()).isEqualTo(c.getFirstName());
                        assertThat(c.getRentals()).as("rentals of staff %d", c.getStaffId())
                                .isEqualTo(countRentalsOf(c.getStaffId()));
                    });
        }

        /**
         * Asserts that the counts of all staff members add up to the number of all rentals: every rental has a staff
         * member, so every rental is counted exactly once.
         */
        @Test
        void __AddUp() {
            final var page = repositoryInstance().selectRentalCounts(PageRequest.of(0, 5, BY_STAFF_ID));
            assumeThat(page.hasNext()).isFalse();
            final var sum = page.getContent().stream().mapToLong(StaffRentalCount::getRentals).sum();
            assertThat(sum).isEqualTo(rentalRepository.count());
        }
    }

    /**
     * Tests {@link StaffRepository#listManagingOwnStore(Pageable)} ({@code SAKILA-STAFF-I-02}).
     */
    @Nested
    class ListManagingOwnStore_Test {

        /**
         * Asserts, in one transaction, that a page of the given size by id as the exercise sorts is ordered so, and
         * that each staff member's store, loaded lazily inside the transaction, names that very staff member as its
         * manager.
         * <p>
         * The page is read inside the transaction, so its staff members are managed, and so are the stores their
         * lazy associations load.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        @Transactional
        void __Transactional(final int size) {
            final var page = repositoryInstance().listManagingOwnStore(PageRequest.of(0, size, BY_STAFF_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(Staff::getStaffId))
                    .allSatisfy(s -> {
                        assertThat(s.getStore().getManagerStaff()).isSameAs(s);
                    });
        }

        /**
         * Asserts, with no transaction, that each staff member listed manages a store, read through
         * {@link StoreRepository} by a {@link Specification}, and that the store they work at is that one, compared by
         * id; and that a selected staff member is listed exactly when the store they manage is the one they work at.
         * <p>
         * Outside a transaction a staff member's store is an uninitialized proxy, whose id is still readable, so the
         * stores are compared by id, not loaded.
         *
         * @param staff one of the staff members selected in {@link StaffRepository_SpringBootIT#__()}.
         */
        @MethodSource("staff")
        @ParameterizedTest
        void __Selected(final Staff staff) {
            final var all = repositoryInstance().listManagingOwnStore(Pageable.unpaged(BY_STAFF_ID));
            assertThat(all.getContent()).allSatisfy(s -> {
                assertThat(storesManagedBy(s)).singleElement().satisfies(st -> {
                    assertThat(st.getStoreId()).isEqualTo(s.getStore().getStoreId());
                });
            });
            final var managesOwn = storesManagedBy(staff).stream()
                    .anyMatch(st -> st.getStoreId().equals(staff.getStore().getStoreId()));
            if (managesOwn) {
                assertThat(all.getContent()).contains(staff);
            } else {
                assertThat(all.getContent()).doesNotContain(staff);
            }
        }

        private static Stream<Staff> staff() {
            return StaffRepository_SpringBootIT.staff();
        }
    }

    /**
     * Tests {@link StaffRepository#selectBestDays(Pageable)} ({@code SAKILA-STAFF-A-01}).
     */
    @Nested
    class SelectBestDays_Test {

        private static final Sort BY_STAFF_ID = Sort.by("staffId");

        private static Stream<Staff> staff() {
            return StaffRepository_SpringBootIT.staff();
        }

        /**
         * Asserts that all the best days, by the id as the exercise sorts, are the rows the exercise shows, on the
         * sample data: the day, the number of payments and their sum, of each staff member.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectBestDays(Pageable.unpaged(BY_STAFF_ID));
            assertThat(page.getContent())
                    .extracting(StaffBestDay::getStaffId, StaffBestDay::getPaymentDay, StaffBestDay::getPayments,
                                d -> d.getTotal().doubleValue())
                    .containsExactly(
                            tuple(1, LocalDate.of(2005, 8, 1), 345L, 1442.55d),
                            tuple(2, LocalDate.of(2005, 7, 31), 351L, 1517.49d)
                    );
            assertThat(page.getTotalElements()).isEqualTo(page.getNumberOfElements());
        }

        /**
         * Asserts that a selected staff member who took a payment has exactly one best day, the sample data having no
         * tie, and that the day's payments are as many as its count says.
         *
         * @param staff one of the staff members selected in {@link StaffRepository_SpringBootIT#__()}.
         */
        @MethodSource("staff")
        @ParameterizedTest
        void __(final Staff staff) {
            final var page = repositoryInstance().selectBestDays(Pageable.unpaged(BY_STAFF_ID));
            assertThat(page.getContent())
                    .filteredOn(d -> d.getStaffId().equals(staff.getStaffId()))
                    .singleElement()
                    .satisfies(d -> {
                        assertThat(d.getPayments()).isPositive();
                        assertThat(d.getTotal()).isPositive();
                    });
        }

        /**
         * Asserts that pages of one walk the same days, in the same order, as the unpaged page, and that the count
         * query counts as many days as there are.
         */
        @Test
        void __Paged() {
            final var all = repositoryInstance().selectBestDays(Pageable.unpaged(BY_STAFF_ID));
            var page = repositoryInstance().selectBestDays(PageRequest.of(0, 1, BY_STAFF_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getNumberOfElements());
            for (final var expected : all) {
                assertPage(page);
                assertThat(page.getContent())
                        .extracting(StaffBestDay::getStaffId, StaffBestDay::getPaymentDay)
                        .containsExactly(tuple(expected.getStaffId(), expected.getPaymentDay()));
                if (page.hasNext()) {
                    page = repositoryInstance().selectBestDays(page.nextPageable());
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private StoreRepository storeRepository;
}
