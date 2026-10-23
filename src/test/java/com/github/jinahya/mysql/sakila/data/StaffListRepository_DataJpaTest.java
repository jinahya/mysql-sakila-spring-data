package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.StaffList;
import com.github.jinahya.mysql.sakila.persistence.StaffList_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link StaffListRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. The slice generates {@code staff_list} as a table, from the entity,
 * but every column of it except the id is read-only ({@code insertable = false}), so no row can be persisted into it,
 * and the fixtures do not try; nor can a staff member, who needs a store. Each test here runs its query against the
 * empty table, and asserts an empty page, or a zero: that proves the JPQL, or the criteria query, is valid for
 * Hibernate and runs on H2, which is what a slice test of a view can prove. What the queries return on the sample
 * data is for {@link StaffListRepository_SpringBootIT}.
 *
 * @see StaffListRepository_SpringBootIT
 */
class StaffListRepository_DataJpaTest
        extends _Repository_DataJpaTest<StaffListRepository, StaffList, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code StaffList_.id} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, StaffList_.ID);

    /**
     * Creates a new instance.
     */
    StaffListRepository_DataJpaTest() {
        super(StaffListRepository.class, StaffList.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link StaffListRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The table is empty, so any id misses; the id is drawn from the whole {@code int} range all the same.
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
     * Tests {@link StaffListRepository#listAll(Pageable)} ({@code SAKILA-STAFF_LIST-B-01}).
     */
    @Nested
    class ListAll_Test {

        /**
         * Asserts that the empty table lists an empty page, by id as the exercise sorts, with no elements in all, and
         * so does an unpaged request.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().listAll(PageRequest.of(0, 5, BY_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            final var all = repositoryInstance().listAll(Pageable.unpaged(BY_ID));
            assertThat(all).isEmpty();
        }
    }

    /**
     * Tests {@link StaffListRepository#selectRowCounts()} ({@code SAKILA-STAFF_LIST-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        /**
         * Asserts that the select has its one row even over the empty view, and that both counts are zero: the view
         * is empty, and no staff member can be saved into the slice.
         * <p>
         * The second count is a subquery in the select, on {@code Staff}; the test proves that Hibernate accepts it
         * there and that H2 runs it.
         */
        @Test
        void _Zero_Empty() {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts).isNotNull();
            assertThat(counts.getStaffLists()).isZero();
            assertThat(counts.getStaffs()).isZero();
        }
    }
}
