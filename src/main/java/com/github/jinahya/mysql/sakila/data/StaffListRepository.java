package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.StaffList;
import com.github.jinahya.mysql.sakila.persistence.StaffList_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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
 * method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA-STAFF_LIST-B-01}, ...) and
 * its role: the book's pick, or this module's own extra; the section has no more. A Basic exercise is plain: its
 * method takes no {@link Pageable}, the order the exercise asks for is written in the query, and it returns a
 * {@link List}. An Intermediate exercise that lists rows takes a {@link Pageable}, the page and the sort, and returns a
 * {@link Page}, so the order the exercise asks for is the caller's {@code PageRequest}; each method's javadoc says
 * which sort that is. An exercise that shows columns of other entities, or counts of two tables, cannot be a
 * {@link Specification}, which only filters; such a method, {@code select...}, declares its JPQL with {@link Query} and
 * returns a projection, one of the interfaces declared here, which Spring Data backs with the selected columns, each
 * aliased with the name of the getter it feeds. Those aliases are what the {@code Pageable} sorts by.
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
     * A row of the view, by id and name, with the city the staff member lives in and the city of the store the staff
     * member works at: a projection of {@link #selectCities(Pageable)}, sortable by {@code id}, {@code name},
     * {@code livesIn} and {@code worksIn}.
     */
    interface StaffCities {

        /**
         * Returns the id of the staff member.
         *
         * @return the id of the staff member.
         */
        Integer getId();

        /**
         * Returns the name of the staff member, the first name and the last name as one.
         *
         * @return the name of the staff member.
         */
        String getName();

        /**
         * Returns the city of the staff member's own address.
         *
         * @return the city the staff member lives in.
         */
        String getLivesIn();

        /**
         * Returns the city of the address of the store the staff member works at.
         *
         * @return the city the staff member works in.
         */
        String getWorksIn();
    }

    /**
     * Finds every row of the view ({@code SAKILA-STAFF_LIST-B-01}, the book's pick, which orders by
     * {@link StaffList_#ID}).
     * <p>
     * The query is derived from the method name, whose {@code OrderBy} is the exercise's order. The sample data has
     * two staff members, so the list holds two rows.
     *
     * @return a list of every row of the view, by id; empty if there is none.
     */
    List<StaffList> findAllByOrderByIdAsc();

    /**
     * Selects how many rows the view has, next to how many staff members the {@code staff} table has
     * ({@code SAKILA-STAFF_LIST-I-01}, this module's own extra, which checks that the two are equal; one row, so
     * no sort).
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

    /**
     * Selects each row of the view, by id and name, with the city the staff member lives in, the view's own
     * {@link StaffList_#CITY}, and the city of the store the staff member works at ({@code SAKILA-STAFF_LIST-I-02}, the
     * book's pick, which orders by {@code id}).
     * <p>
     * The view names the store by its id only, {@link StaffList_#SID}, which is no association, so the store is a
     * second root, joined by that id in the {@code WHERE}, and its address and city are joined from it. The columns
     * come from two roots, which a {@link Specification} of this one cannot select; hence a {@link Query} and a
     * projection. The count query counts the rows the same join keeps.
     *
     * @param pageable the page to return, and its sort, by {@code id}, {@code name}, {@code livesIn} or
     *                 {@code worksIn}; must not be {@code null}.
     * @return a page of the rows of the view, with the two cities.
     */
    @Query(value = """
            SELECT l.id AS id, l.name AS name, l.city AS livesIn, ci.city AS worksIn
            FROM StaffList l, Store st
            JOIN st.address a
            JOIN a.city ci
            WHERE st.storeId = l.sid""",
           countQuery = """
                   SELECT COUNT(l)
                   FROM StaffList l, Store st
                   JOIN st.address a
                   JOIN a.city ci
                   WHERE st.storeId = l.sid""")
    Page<StaffCities> selectCities(Pageable pageable);
}
