package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Staff;
import com.github.jinahya.mysql.sakila.persistence.Staff_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link StaffRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. No staff member can be persisted into the H2: a staff member works
 * at a store, which names its manager, a staff member, both {@code NOT NULL}, so neither of the two can be saved first
 * (see {@link _Repository_DataJpaTest_Fixtures}). Each test here runs its query against an empty {@code staff} table,
 * and asserts an empty page; that still proves that the JPQL, or the criteria query, is valid and runs on the H2,
 * with the sort the exercise asks for.
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
     * Tests {@link StaffRepository#listWithStore(Pageable)} ({@code SAKILA-STAFF-B-01}).
     */
    @Nested
    class ListWithStore_Test {

        /**
         * Asserts that the criteria query with its fetch, sorted by id as the exercise sorts, runs on the empty
         * tables and lists an empty page, with no staff members in all.
         * <p>
         * The page runs two queries, the select with the fetch and the count without it; both have to be valid.
         */
        @Test
        void _Empty_NoStaff() {
            final var page = repositoryInstance().listWithStore(PageRequest.of(0, 5, BY_STAFF_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link StaffRepository#selectRentalCounts(Pageable)} ({@code SAKILA-STAFF-I-01}).
     */
    @Nested
    class SelectRentalCounts_Test {

        private static final Sort BY_STAFF_ID = Sort.by("staffId");

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
    }

    /**
     * Tests {@link StaffRepository#listManagingOwnStore(Pageable)} ({@code SAKILA-STAFF-I-02}).
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
    }
}
