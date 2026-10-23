package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.CustomerList;
import com.github.jinahya.mysql.sakila.persistence.CustomerList_;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

/**
 * A repository for {@link CustomerList}, the {@code customer_list} view of the Sakila database.
 * <p>
 * The view is read-only: it joins each customer of the {@code customer} table with their address, city and country,
 * writes the two names as one, and says, as {@code SID}, which store the customer belongs to. A row is identified by
 * {@link CustomerList_#ID}, the id of the customer it is about, which is the only column upstream maps as insertable,
 * since Jakarta Persistence requires an {@code @Id} that is not read-only; every other column is
 * {@code insertable = false, updatable = false}. Nothing is ever saved through this repository: a view accepts no
 * insert, and the Sakila database is only read. {@link CustomerList#equals(Object)} compares ids, and only once they
 * are assigned.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.equal(root.get(CustomerList_.country), country)}.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code customer_list} view,
 * one method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA-CUSTOMER_LIST-B-01},
 * ...), and by its role: the book's pick, which every data module answers, this module's own extra, a native-only
 * one, or, for one kept from before the picks, not a pick. A Basic method is plain: the book's pick returns a
 * {@link List}, this module's own extra a {@link Stream}, in the order the exercise asks for, written in the query.
 * An Intermediate or Advanced method takes a {@link Pageable}, the page and the sort, and returns a {@link Page}, so
 * the order the exercise asks for, and its {@code LIMIT}, are the caller's {@code PageRequest}; each method's javadoc
 * says which sort that is. An exercise that shows columns, grouped and counted, or counts of two tables, cannot be a
 * {@link Specification}, which only filters; such a method, {@code select...}, declares its JPQL with {@link Query},
 * with a count query of its own where it is paged, since Spring Data cannot derive one from a grouped select. A query
 * selects entities, not their columns, wherever it groups by an entity; every query here groups by a value of the
 * view, not by an entity (the {@code SID}, the notes, a city and its country), or counts only, so it still selects
 * those values. Such a result comes back as a {@link jakarta.persistence.Tuple}, each element aliased in the select
 * and read by that alias, with its type ({@code tuple.get("customers", Long.class)}); the aliases are also what the
 * {@code Pageable} sorts by ({@code Sort.by(DESC, "customers")}). Each method's javadoc names its aliases.
 * <p>
 * An exercise only native SQL can answer, here a top three of each store, which takes a window function JPQL does
 * not have, is a {@link NativeQuery} of the exercise's own SQL, with its order and its limit, returning a list of
 * tuples read by the select's aliases.
 *
 * @see CustomerList
 * @see CustomerList_
 */
@Repository
public interface CustomerListRepository
        extends _Repository<CustomerList, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Finds the rows whose value of {@link CustomerList_#COUNTRY} attribute is equal to the specified value, ordered
     * by {@link CustomerList_#ID}, as a stream ({@code SAKILA-CUSTOMER_LIST-B-01}, this module's own extra, which
     * asks for the customers in {@code Japan}, ordered by {@link CustomerList_#ID}).
     * <p>
     * The query is derived from the method name, its order too; an equality on the country as the view writes it, with
     * no join, since the view already joined the address chain. The comparison is the database's, which on the Sakila
     * database is case-insensitive, by the collation of the column.
     * <p>
     * A {@link Stream} reads the rows as they are consumed, over a result set that stays open until the stream is
     * closed, so close it, in a try-with-resources. It also has to be consumed inside a transaction the caller opened:
     * the transaction the repository opens for itself ends when this method returns, which would close the result set
     * under the stream, so Spring Data refuses a call that no transaction surrounds, with an
     * {@link org.springframework.dao.InvalidDataAccessApiUsageException}, before any query runs.
     *
     * @param country the value of the {@link CustomerList_#COUNTRY} attribute to match; must not be {@code null}.
     * @return a stream of the rows whose country is {@code country}, by id, to be closed by the caller; empty if there
     *         is none.
     */
    Stream<CustomerList> findAllByCountryOrderByIdAsc(String country);

    /**
     * Selects each store's id, as the view's {@code SID} says it, with how many customers the view puts in that store
     * ({@code SAKILA-CUSTOMER_LIST-B-02}, not a pick, which orders by {@code sid}).
     * <p>
     * The rows are grouped by {@code SID} and counted, which a {@link Specification}, which filters rows, cannot do.
     * The count query counts the distinct {@code SID}s, which are the groups. The counts should match those taken from
     * the {@code customer} table itself, by store.
     *
     * @param pageable the page to return, and its sort, by {@code sid} or {@code customers}; must not be {@code null}.
     * @return a page of the store ids with their numbers of customers, each a tuple of {@code sid} ({@link Integer})
     *         and {@code customers} ({@link Long}).
     */
    @Query(value = """
            SELECT v.sid AS sid, COUNT(v) AS customers
            FROM CustomerList v
            GROUP BY v.sid""",
           countQuery = """
                   SELECT COUNT(DISTINCT v.sid)
                   FROM CustomerList v""")
    Page<Tuple> selectCustomerCountsBySid(Pageable pageable);

    /**
     * Selects each value of the view's {@link CustomerList_#NOTES} with the number of customers who have it
     * ({@code SAKILA-CUSTOMER_LIST-B-03}, the book's pick, which orders by {@code notes}).
     * <p>
     * The view writes {@code active} for an active customer and an empty string for an inactive one, so the rows are
     * grouped by that and counted, in the order of the notes, written in the query; the empty string sorts first.
     *
     * @return a list of the values of the notes, with their numbers of customers, by the notes, each a tuple of
     *         {@code notes} ({@link String}) and {@code customers} ({@link Long}).
     */
    @Query("""
            SELECT v.notes AS notes, COUNT(v) AS customers
            FROM CustomerList v
            GROUP BY v.notes
            ORDER BY v.notes""")
    List<Tuple> selectNotesCounts();

    /**
     * Selects how many rows the view has, next to how many customers the {@code customer} table has
     * ({@code SAKILA-CUSTOMER_LIST-I-01}, this module's own extra, which checks that the two are equal; one row, so
     * the sort, by {@code customerLists} or {@code customers}, changes nothing).
     * <p>
     * The view uses inner joins, so a customer whose address chain is broken would be missing from it; the two counts
     * should be equal. The second count is a subquery in the select, on another entity, which a {@link Specification}
     * of this one cannot select; hence a {@link Query} and a tuple. The select aggregates every row of the view
     * into one, so it always has exactly one row, even when the view is empty; the count query says so, by counting
     * the one row of the same aggregate.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the one row of the two counts, a tuple of {@code customerLists} and {@code customers}
     *         ({@link Long}).
     */
    @Query(value = """
            SELECT COUNT(v) AS customerLists, (SELECT COUNT(c) FROM Customer c) AS customers
            FROM CustomerList v""",
           countQuery = """
                   SELECT COUNT(*)
                   FROM (SELECT COUNT(v) AS customerLists
                         FROM CustomerList v) t""")
    Page<Tuple> selectRowCounts(Pageable pageable);

    /**
     * Selects the cities, with their countries, where the view puts more than the specified number of customers
     * ({@code SAKILA-CUSTOMER_LIST-I-02}, the book's pick, which asks for more than one customer, ordered by
     * {@code city}, then by {@code country}).
     * <p>
     * The rows are grouped by city and country, since a city's name alone is not unique, and counted; the filter is on
     * the groups, so it is a {@code HAVING}. The count query counts one row of each group that passes, the one of the
     * smallest id, through correlated subqueries on the same city and country.
     *
     * @param customers the number of customers a city has to have more than.
     * @param pageable  the page to return, and its sort, by {@code city}, {@code country} or {@code customers}; must
     *                  not be {@code null}.
     * @return a page of the cities with more than {@code customers} customers, with their countries and counts, each
     *         a tuple of {@code city} and {@code country} ({@link String}), and {@code customers} ({@link Long}).
     */
    @Query(value = """
            SELECT v.city AS city, v.country AS country, COUNT(v) AS customers
            FROM CustomerList v
            GROUP BY v.city, v.country
            HAVING COUNT(v) > :customers""",
           countQuery = """
                   SELECT COUNT(v)
                   FROM CustomerList v
                   WHERE v.id = (SELECT MIN(v2.id)
                                 FROM CustomerList v2
                                 WHERE v2.city = v.city AND v2.country = v.country)
                     AND (SELECT COUNT(v3)
                          FROM CustomerList v3
                          WHERE v3.city = v.city AND v3.country = v.country) > :customers""")
    Page<Tuple> selectCitiesWithMoreCustomersThan(long customers, Pageable pageable);

    /**
     * Selects the three countries with the most customers of each store ({@code SAKILA-CUSTOMER_LIST-A-01},
     * native-only, ordered by {@code sid}, then by {@code customers} descending, then by {@code country}, in the
     * query).
     * <p>
     * The rows are grouped by store and country and counted, and the countries numbered within their store by
     * {@code ROW_NUMBER()}, the most customers first, ties broken by the name; the first three of each store are kept.
     * JPQL has no window function, so this is the exercise's own SQL, with each column aliased.
     *
     * @return a list of the three countries with the most customers of each store, store by store, each a tuple of
     *         {@code sid} (a number, as the JDBC driver types it), {@code country} ({@link String}) and
     *         {@code customers} ({@link Long}).
     */
    @NativeQuery("""
            SELECT SID AS sid, country, customers
            FROM (SELECT SID, country, COUNT(*) AS customers,
                         ROW_NUMBER() OVER (PARTITION BY SID ORDER BY COUNT(*) DESC, country) AS n
                  FROM customer_list
                  GROUP BY SID, country) t
            WHERE n <= 3
            ORDER BY SID, n""")
    List<Tuple> selectTopThreeCountriesOfEachStore();
}
