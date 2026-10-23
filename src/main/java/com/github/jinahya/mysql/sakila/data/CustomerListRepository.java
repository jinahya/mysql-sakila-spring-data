package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.CustomerList;
import com.github.jinahya.mysql.sakila.persistence.CustomerList_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Objects;

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
 * ...), one or two of each level. The exercises are context, not contract: a method that lists rows takes a
 * {@link Pageable}, the page and the sort, and returns a {@link Page}, so the order the exercise asks for, and its
 * {@code LIMIT}, are the caller's {@code PageRequest}; each method's javadoc says which sort that is. An exercise
 * that shows rows is a {@code default} method, {@code list...}, which builds a {@link Specification} with the Criteria
 * API and runs it through {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An exercise that
 * shows columns, grouped and counted, or counts of two tables, cannot be a {@code Specification}, which only filters;
 * such a method, {@code select...}, declares its JPQL with {@link Query}, with a count query of its own where the
 * select is grouped, since Spring Data cannot derive one from a grouped select, and returns a projection, one of the
 * interfaces declared here, which Spring Data backs with the selected columns, each aliased with the name of the
 * getter it feeds. Those aliases are what the {@code Pageable} sorts by; an interface rather than a record, because
 * Spring Data rewrites a record projection into a constructor expression, which drops the aliases, and the sort with
 * them.
 *
 * @see CustomerList
 * @see CustomerList_
 */
@Repository
public interface CustomerListRepository
        extends _Repository<CustomerList, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * A store's id, as the view's {@code SID} says it, and how many customers the view puts in that store: a
     * projection of {@link #selectCustomerCountsBySid(Pageable)}, sortable by {@code sid} and {@code customers}.
     */
    interface SidCustomerCount {

        /**
         * Returns the id of the store.
         *
         * @return the id of the store.
         */
        Integer getSid();

        /**
         * Returns the number of customers of the store.
         *
         * @return the number of customers.
         */
        long getCustomers();
    }

    /**
     * How many rows the view has, and how many customers the {@code customer} table has: a projection of
     * {@link #selectRowCounts()}.
     */
    interface RowCounts {

        /**
         * Returns the number of rows of the {@code customer_list} view.
         *
         * @return the number of rows of the view.
         */
        long getCustomerLists();

        /**
         * Returns the number of rows of the {@code customer} table.
         *
         * @return the number of customers.
         */
        long getCustomers();
    }

    /**
     * Lists the customers whose value of {@link CustomerList_#COUNTRY} attribute is equal to the specified value
     * ({@code SAKILA-CUSTOMER_LIST-B-01}, which shows the customers in {@code Japan}, ordered by
     * {@link CustomerList_#ID}).
     * <p>
     * A {@link Specification} of one equality, on the country as the view writes it; no join, since the view already
     * joined the address chain. The comparison is the database's, which on the Sakila database is case-insensitive,
     * by the collation of the column.
     *
     * @param country  the value of the {@link CustomerList_#COUNTRY} attribute to match; must not be {@code null}.
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the customers whose country is {@code country}.
     */
    default Page<CustomerList> listByCountry(final String country, final Pageable pageable) {
        Objects.requireNonNull(country, "country is null");
        Objects.requireNonNull(pageable, "pageable is null");
        final Specification<CustomerList> specification = (r, q, b) -> b.equal(r.get(CustomerList_.country), country);
        return findAll(specification, pageable);
    }

    /**
     * Selects each store's id, as the view's {@code SID} says it, with how many customers the view puts in that store
     * ({@code SAKILA-CUSTOMER_LIST-B-02}, which orders by {@code sid}).
     * <p>
     * The rows are grouped by {@code SID} and counted, which a {@link Specification}, which filters rows, cannot do.
     * The count query counts the distinct {@code SID}s, which are the groups. The counts should match those taken from
     * the {@code customer} table itself, by store.
     *
     * @param pageable the page to return, and its sort, by {@code sid} or {@code customers}; must not be {@code null}.
     * @return a page of the store ids with their numbers of customers.
     */
    @Query(value = """
            SELECT v.sid AS sid, COUNT(v) AS customers
            FROM CustomerList v
            GROUP BY v.sid""",
           countQuery = """
                   SELECT COUNT(DISTINCT v.sid)
                   FROM CustomerList v""")
    Page<SidCustomerCount> selectCustomerCountsBySid(Pageable pageable);

    /**
     * Selects how many rows the view has, next to how many customers the {@code customer} table has
     * ({@code SAKILA-CUSTOMER_LIST-I-01}, which checks that the two are equal; one row, so no sort).
     * <p>
     * The view uses inner joins, so a customer whose address chain is broken would be missing from it; the two counts
     * should be equal. The second count is a subquery in the select, on another entity, which a {@link Specification}
     * of this one cannot select; hence a {@link Query} and a projection. The select aggregates every row of the view
     * into one, so it always has exactly one row, even when the view is empty, and takes no {@link Pageable}.
     *
     * @return the two counts.
     */
    @Query("""
            SELECT COUNT(v) AS customerLists, (SELECT COUNT(c) FROM Customer c) AS customers
            FROM CustomerList v""")
    RowCounts selectRowCounts();
}
