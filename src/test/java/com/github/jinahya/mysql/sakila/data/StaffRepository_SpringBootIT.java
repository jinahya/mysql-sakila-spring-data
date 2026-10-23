package com.github.jinahya.mysql.sakila.data;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
     * Tests {@link StaffRepository#listWithStore(Pageable)} ({@code SAKILA-STAFF-B-01}).
     */
    @Nested
    class ListWithStore_Test {

        private static Stream<Staff> staff() {
            return StaffRepository_SpringBootIT.staff();
        }

        /**
         * Asserts that all staff members, by id as the exercise sorts, are ordered so, as many as the table holds,
         * with a selected one among them, and that each one's store is already initialized, which only the fetch
         * can have done: the test runs in no transaction, so a lazy store would be an uninitialized proxy here.
         *
         * @param staff one of the staff members selected in {@link StaffRepository_SpringBootIT#__()}.
         */
        @MethodSource("staff")
        @ParameterizedTest
        void __(final Staff staff) {
            final var all = repositoryInstance().listWithStore(Pageable.unpaged(BY_STAFF_ID));
            assertThat(all.getTotalElements()).isEqualTo(repositoryInstance().count());
            assertThat(all.getContent())
                    .contains(staff)
                    .isSortedAccordingTo(Comparator.comparing(Staff::getStaffId))
                    .allSatisfy(s -> {
                        assertThat(Hibernate.isInitialized(s.getStore())).as("store of staff %d", s.getStaffId())
                                .isTrue();
                        assertThat(s.getStore().getStoreId()).isNotNull();
                        assertThat(s.getActive()).isNotNull();
                    });
        }

        /**
         * Asserts that pages of the given size walk the same staff members, in the same order, as the unpaged list,
         * each page's stores fetched too.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 2})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().listWithStore(Pageable.unpaged(BY_STAFF_ID));
            var page = repositoryInstance().listWithStore(PageRequest.of(0, size, BY_STAFF_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            final var walked = new ArrayList<Staff>();
            while (true) {
                assertPage(page);
                assertThat(page.getContent()).allSatisfy(s -> assertThat(Hibernate.isInitialized(s.getStore()))
                        .isTrue());
                walked.addAll(page.getContent());
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().listWithStore(page.nextPageable());
            }
            assertThat(walked).containsExactlyElementsOf(all.getContent());
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

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private StoreRepository storeRepository;
}
