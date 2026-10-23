package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.StaffList;
import com.github.jinahya.mysql.sakila.persistence.StaffList_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * A repository for {@link StaffList}, the {@code staff_list} view of the Sakila database.
 * <p>
 * The view is read-only: it joins each staff member of the {@code staff} table with their address, city and country,
 * writes the two names as one, and says, as {@code SID}, which store the staff member works at. A row is identified
 * by {@link StaffList_#ID}, the id of the staff member it is about, which is the only column upstream maps as
 * insertable, since Jakarta Persistence requires an {@code @Id} that is not read-only; every other column is
 * {@code insertable = false, updatable = false}. Nothing is ever saved through this repository: a view accepts no
 * insert, and the Sakila database is only read. {@link StaffList#equals(Object)} compares ids, and only once they are
 * assigned.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.equal(root.get(StaffList_.sid), sid)}.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code staff_list} view, one
 * method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA-STAFF_LIST-B-01}, ...), one
 * of each level, which is all the section has. The exercises are context, not contract: a method that lists rows
 * takes a {@link Pageable}, the page and the sort, and returns a {@link Page}, so the order the exercise asks for is
 * the caller's {@code PageRequest}; each method's javadoc says which sort that is. An exercise that shows rows is a
 * {@code default} method, {@code list...}, which builds a {@link Specification} with the Criteria API and runs it
 * through {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An exercise that shows counts of
 * two tables cannot be a {@code Specification}, which only filters; such a method, {@code select...}, declares its
 * JPQL with {@link Query} and returns a projection, one of the interfaces declared here, which Spring Data backs with
 * the selected columns, each aliased with the name of the getter it feeds.
 *
 * @see StaffList
 * @see StaffList_
 */
@Repository
public interface StaffListRepository
        extends _Repository<StaffList, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * How many rows the view has, and how many staff members the {@code staff} table has: a projection of
     * {@link #selectRowCounts()}.
     */
    interface RowCounts {

        /**
         * Returns the number of rows of the {@code staff_list} view.
         *
         * @return the number of rows of the view.
         */
        long getStaffLists();

        /**
         * Returns the number of rows of the {@code staff} table.
         *
         * @return the number of staff members.
         */
        long getStaffs();
    }

    /**
     * Lists every row of the view ({@code SAKILA-STAFF_LIST-B-01}, which orders by {@link StaffList_#ID}).
     * <p>
     * A {@link Specification} that restricts nothing, {@link Specification#unrestricted()}, run through
     * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}, so that the page and the sort are the
     * caller's, as for the other exercises. The sample data has two staff members, so one page by id holds them both.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of every row of the view.
     */
    default Page<StaffList> listAll(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        final Specification<StaffList> specification = Specification.unrestricted();
        return findAll(specification, pageable);
    }

    /**
     * Selects how many rows the view has, next to how many staff members the {@code staff} table has
     * ({@code SAKILA-STAFF_LIST-I-01}, which checks that the two are equal; one row, so no sort).
     * <p>
     * The view uses inner joins, so a staff member whose address chain is broken would be missing from it; the two
     * counts should be equal. The second count is a subquery in the select, on another entity, which a
     * {@link Specification} of this one cannot select; hence a {@link Query} and a projection. The select aggregates
     * every row of the view into one, so it always has exactly one row, even when the view is empty, and takes no
     * {@link Pageable}.
     *
     * @return the two counts.
     */
    @Query("""
            SELECT COUNT(v) AS staffLists, (SELECT COUNT(s) FROM Staff s) AS staffs
            FROM StaffList v""")
    RowCounts selectRowCounts();
}
