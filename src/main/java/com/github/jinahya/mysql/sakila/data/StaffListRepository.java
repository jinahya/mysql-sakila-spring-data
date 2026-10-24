package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.City;
import com.github.jinahya.mysql.sakila.persistence.StaffList;
import com.github.jinahya.mysql.sakila.persistence.StaffList_;
import jakarta.persistence.Tuple;
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
 * method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA_STAFF_LIST_B_01}, ...) and
 * its role: the book's pick, or this module's own extra; the section has no more. A Basic exercise is plain: its
 * method takes no {@link Pageable}, the order the exercise asks for is written in the query, and it returns a
 * {@link List}. An Intermediate exercise that lists rows takes a {@link Pageable}, the page and the sort, and returns a
 * {@link Page}, so the order the exercise asks for is the caller's {@code PageRequest}; each method's javadoc says
 * which sort that is. An exercise that shows columns of other entities, or counts of two tables, cannot be a
 * {@link Specification}, which only filters; such a method, {@code select...}, declares its JPQL with {@link Query}.
 * Such a query selects entities, not their columns: a row of the view is selected whole
 * ({@code SELECT l AS staffList}), and so is the city of the store it names, beside it; what is computed, a count,
 * is a column of its own. A result that is a single entity is that entity; any other comes back as a
 * {@link jakarta.persistence.Tuple}, each element read by its alias, with its type
 * ({@code tuple.get("staffList", StaffList.class)}, {@code tuple.get("staffs", Long.class)}). The {@code Pageable}
 * sorts a computed column by its alias, and an attribute of a selected entity by its path from the query's
 * identification variable ({@code Sort.by("l.id")}); each method's javadoc names its aliases and identification
 * variables. A query whose select has no entity, only counts, stays as it is.
 *
 * @see StaffList
 * @see StaffList_
 */
@Repository
public interface StaffListRepository
        extends _Repository<StaffList, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Finds every row of the view ({@code SAKILA_STAFF_LIST_B_01}, the book's pick, which orders by
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
     * ({@code SAKILA_STAFF_LIST_I_01}, this module's own extra, which checks that the two are equal; one row, so
     * no sort).
     * <p>
     * The view uses inner joins, so a staff member whose address chain is broken would be missing from it; the two
     * counts should be equal. The second count is a subquery in the select, on another entity, which a
     * {@link Specification} of this one cannot select; hence a {@link Query} and a tuple. The select aggregates
     * every row of the view into one, so it always has exactly one row, even when the view is empty, and takes no
     * {@link Pageable}.
     *
     * @return the two counts, as a tuple of {@code staffLists} and {@code staffs} ({@link Long}).
     */
    @Query("""
            SELECT COUNT(v) AS staffLists, (SELECT COUNT(s) FROM Staff s) AS staffs
            FROM StaffList v""")
    Tuple selectRowCounts();

    /**
     * Selects each row of the view, which carries the city the staff member lives in, the view's own
     * {@link StaffList_#CITY}, with the city of the store the staff member works at ({@code SAKILA_STAFF_LIST_I_02},
     * the book's pick, which orders by {@code l.id}).
     * <p>
     * The view names the store by its id only, {@link StaffList_#SID}, which is no association, so the store is a
     * second root, joined by that id in the {@code WHERE}, and its address and city are joined from it. The row and
     * the store's city are selected whole; they come from two roots, which a {@link Specification} of this one cannot
     * select; hence a {@link Query} and a tuple. The count query counts the rows the same join keeps.
     *
     * @param pageable the page to return, and its sort, by an attribute of the row through {@code l} ({@code l.id},
     *                 {@code l.name}, {@code l.city}, ...) or of the store's city through {@code ci}
     *                 ({@code ci.city}, ...); must not be {@code null}.
     * @return a page of the rows of the view, with the cities of the stores, each a tuple of {@code staffList}
     *         ({@link StaffList}) and {@code city} ({@link City}).
     */
    @Query(value = """
            SELECT l AS staffList, ci AS city
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
    Page<Tuple> selectCities(Pageable pageable);
}
