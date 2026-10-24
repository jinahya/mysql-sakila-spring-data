package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Staff;
import com.github.jinahya.mysql.sakila.persistence.Staff_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link StaffRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No staff member can be persisted into the H2: a staff member works
 * at a store, which names its manager, a staff member, both {@code NOT NULL}, so neither of the two can be saved first
 * (see {@link _Repository_DataJpaTest_Fixtures}). Each test here runs its query against an empty {@code staff} table,
 * and asserts an empty list, stream or page; that still proves that the derived query, the JPQL, or the criteria
 * query, is valid and runs on the H2, with the sort the exercise asks for.
 *
 * @see StaffRepository_SpringBootIT
 */
class StaffRepository_DataJpaTest
        extends _Repository_DataJpaTest<StaffRepository, Staff, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Staff_.staffId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_STAFF_ID = Sort.by(Sort.Direction.ASC, Staff_.STAFF_ID);

    /**
     * Creates a new instance.
     */
    StaffRepository_DataJpaTest() {
        super(StaffRepository.class, Staff.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link StaffRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The table is empty, and stays so, so any id misses.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link StaffRepository#findAllByOrderByStaffIdAsc()} ({@code SAKILA_STAFF_B_01}).
     */
    @Nested
    class FindAllByOrderByStaffIdAsc_Test {

        /**
         * Asserts that the derived query, ordered by id as the exercise orders, with the entity graph that fetches the
         * store, runs on the empty tables and finds an empty list.
         */
        @Test
        void _Empty_NoStaff() {
            final var found = repositoryInstance().findAllByOrderByStaffIdAsc();
            assertThat(found).isEmpty();
        }
    }

    /**
     * Tests {@link StaffRepository#findAllByPasswordIsNullOrderByStaffIdAsc()} ({@code SAKILA_STAFF_B_02}).
     */
    @Nested
    class FindAllByPasswordIsNullOrderByStaffIdAsc_Test {

        /**
         * Asserts that the derived query, ordered by id as the exercise orders, runs on the empty table, inside the
         * test's transaction, and streams nothing.
         */
        @Test
        void _Empty_NoStaff() {
            try (var found = repositoryInstance().findAllByPasswordIsNullOrderByStaffIdAsc()) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}, though the table is empty.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
     * Tests {@link StaffRepository#selectRentalCounts(Pageable)} ({@code SAKILA_STAFF_I_01}).
     */
    @Nested
    class SelectRentalCounts_Test {

        private static final Sort BY_STAFF_ID = Sort.by("s.staffId");

        /**
         * Asserts that the joined and grouped select, sorted by the id as the exercise sorts, runs on the empty
         * tables and selects an empty page, with no staff members in all.
         */
        @Test
        void _Empty_NoRentals() {
            final var page = repositoryInstance().selectRentalCounts(PageRequest.of(0, 5, BY_STAFF_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument, and throws
         * {@link IllegalArgumentException}.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectRentalCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link StaffRepository#listManagingOwnStore(Pageable)} ({@code SAKILA_STAFF_I_02}).
     */
    @Nested
    class ListManagingOwnStore_Test {

        /**
         * Asserts that the joined criteria query, sorted by id as the exercise sorts, runs on the empty tables and
         * lists an empty page, with no staff members in all.
         */
        @Test
        void _Empty_NoStaff() {
            final var page = repositoryInstance().listManagingOwnStore(PageRequest.of(0, 5, BY_STAFF_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before the method builds its query.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument even of a {@code default} method, and
         * throws {@link IllegalArgumentException} before the method's own {@code requireNonNull} is reached.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listManagingOwnStore(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link StaffRepository#selectBestDays(Pageable)} ({@code SAKILA_STAFF_A_01}).
     */
    @Nested
    class SelectBestDays_Test {

        private static final Sort BY_STAFF_ID = Sort.by("s.staffId");

        /**
         * Asserts that the grouped select, with its {@code HAVING ... >= ALL} on a correlated subquery, and its count
         * query, sorted by the id as the exercise sorts, run on the empty tables and select an empty page, with no
         * days in all.
         */
        @Test
        void _Empty_NoPayments() {
            final var page = repositoryInstance().selectBestDays(PageRequest.of(0, 5, BY_STAFF_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is rejected before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the argument, and throws
         * {@link IllegalArgumentException}.
         */
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectBestDays(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
