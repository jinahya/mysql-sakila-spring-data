package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory;
import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory_;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A repository for {@link SalesByFilmCategory}, the {@code sales_by_film_category} view of the Sakila database.
 * <p>
 * The view is read-only: it sums, for each category of the {@code category} table, the {@code amount} of the payments
 * of the rentals of the films of that category, as {@code total_sales}. A row is identified by
 * {@link SalesByFilmCategory_#CATEGORY}, the name of the category, a {@link String}, which is the only column
 * upstream maps as insertable, since Jakarta Persistence requires an {@code @Id} that is not read-only; the
 * {@link SalesByFilmCategory_#TOTAL_SALES} is {@code insertable = false, updatable = false}. Nothing is ever saved
 * through this repository: a view accepts no insert, and the Sakila database is only read.
 * {@link SalesByFilmCategory#equals(Object)} compares the names, and only once they are assigned.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.greaterThan(root.get(SalesByFilmCategory_.totalSales), amount)}.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the
 * {@code sales_by_film_category} view, one method per exercise, named in each method's javadoc by the exercise's id
 * ({@code SAKILA_SALES_BY_FILM_CATEGORY_B_01}, ...) and its role: the book's pick, this module's own extra, or not a
 * pick, for an exercise this module answered before the picks were made. A Basic pick is plain: its method takes no
 * {@link Pageable}, the order the exercise asks for is written in the query, and it returns a {@link List}. An
 * Intermediate or Advanced exercise that lists rows takes a {@link Pageable}, the page and the sort, and returns a
 * {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are the caller's {@code PageRequest}; each
 * method's javadoc says which sort that is. An exercise that shows rows as they are is a {@code default} method,
 * {@code list...}, which builds a {@link Specification} with the Criteria API and runs it through
 * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An exercise that shows sums, shares or
 * differences cannot be a {@code Specification}, which only filters; such a method, {@code select...}, declares its
 * JPQL with {@link Query}, with a count query of its own. Such a query selects entities, not their columns: a row of
 * the view is selected whole ({@code SELECT s AS salesByFilmCategory}), and grouped by itself ({@code GROUP BY s})
 * where the query groups; what is computed, a sum, a share, a difference, is a column of its own beside it. A result
 * that is a single entity is that entity; any other comes back as a {@link jakarta.persistence.Tuple}, each element
 * read by its alias, with its type ({@code tuple.get("salesByFilmCategory", SalesByFilmCategory.class)},
 * {@code tuple.get("percent", BigDecimal.class)}). The {@code Pageable} sorts a computed column by its alias
 * ({@code Sort.by(DESC, "percent")}), and an attribute of a selected entity by its path from the query's
 * identification variable ({@code Sort.by(DESC, "s.totalSales")}); each method's javadoc names its aliases and
 * identification variables. A query whose select has no entity, only sums, stays as it is.
 *
 * @see SalesByFilmCategory
 * @see SalesByFilmCategory_
 */
@Repository
public interface SalesByFilmCategoryRepository
        extends _Repository<SalesByFilmCategory, String> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Lists every row of the view ({@code SAKILA_SALES_BY_FILM_CATEGORY_B_01}, not a pick, which takes the first
     * three by {@link SalesByFilmCategory_#TOTAL_SALES} descending, the categories which sold the most).
     * <p>
     * The exercise's {@code LIMIT 3} makes a page of it, so it is no Basic pick (a top three is a page, not a list);
     * the method is kept in the {@link Page} shape it was written in.
     * <p>
     * A {@link Specification} that restricts nothing, {@link Specification#unrestricted()}, run through
     * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}, so that the page and the sort are the
     * caller's, as for the other exercises; the exercise's {@code LIMIT 3} is a page of three. The view declares an
     * order of its own, by {@code total_sales} descending, which MySQL keeps when nothing else orders the rows, but a
     * page that relies on it is not stable; give the {@code pageable} the sort.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of every row of the view.
     */
    default Page<SalesByFilmCategory> listAll(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        final Specification<SalesByFilmCategory> specification = Specification.unrestricted();
        return findAll(specification, pageable);
    }

    /**
     * Finds the categories whose sales are between the specified bounds, both included
     * ({@code SAKILA_SALES_BY_FILM_CATEGORY_B_02}, the book's pick, which asks for {@code 4000} and {@code 4500} and
     * orders by {@link SalesByFilmCategory_#CATEGORY}).
     * <p>
     * The query is derived from the method name: a {@code BETWEEN} on {@link SalesByFilmCategory_#TOTAL_SALES}, and
     * the exercise's order.
     *
     * @param low  the lower bound of the sales, included; must not be {@code null}.
     * @param high the upper bound of the sales, included; must not be {@code null}.
     * @return a list of the categories whose sales are between {@code low} and {@code high}, by name; empty if there is
     *         none.
     */
    List<SalesByFilmCategory> findAllByTotalSalesBetweenOrderByCategoryAsc(BigDecimal low, BigDecimal high);

    /**
     * Selects the sum of the view's {@code total_sales} over all categories, next to the sum of the {@code amount} of
     * all payments ({@code SAKILA_SALES_BY_FILM_CATEGORY_I_01}, this module's own extra, which checks that the two are
     * equal; one row, so no sort).
     * <p>
     * The two are equal only if every payment reaches exactly one category, through its rental, copy and film. The
     * second sum is a subquery in the select, on another entity, which a {@link Specification} of this one cannot
     * select; hence a {@link Query} and a tuple. The select aggregates every row of the view into one, so it
     * always has exactly one row, even when the view is empty, and takes no {@link Pageable}; the sums of no rows are
     * {@code null}, not zero.
     *
     * @return the two sums, as a tuple of {@code byCategory} and {@code allPayments} ({@link BigDecimal}), each
     *         {@code null} if there is no row to sum.
     */
    @Query("""
            SELECT SUM(v.totalSales) AS byCategory, (SELECT SUM(p.amount) FROM Payment p) AS allPayments
            FROM SalesByFilmCategory v""")
    Tuple selectSalesTotals();

    /**
     * Selects every category, with its sales and their share of all sales ({@code SAKILA_SALES_BY_FILM_CATEGORY_I_02},
     * the book's pick, which takes the first five by {@code s.totalSales} descending, then by {@code s.category}).
     * <p>
     * The share divides the category's sales by the sales of every category, a scalar subquery in the select, which
     * Hibernate's query language, unlike standard JPQL, allows; nothing is grouped. The row is selected whole, and the
     * share beside it is computed, which a {@link Specification} cannot select; hence a {@link Query} and a tuple. The
     * count query counts the rows of the view.
     *
     * @param pageable the page to return, and its sort, by {@code percent}, or by an attribute of the row through
     *                 {@code s} ({@code s.category}, {@code s.totalSales}); must not be {@code null}.
     * @return a page of the categories, with their sales and shares, each a tuple of {@code salesByFilmCategory}
     *         ({@link SalesByFilmCategory}) and {@code percent} ({@link BigDecimal}).
     */
    @Query(value = """
            SELECT s AS salesByFilmCategory,
                   ROUND(100 * s.totalSales / (SELECT SUM(t.totalSales) FROM SalesByFilmCategory t), 2) AS percent
            FROM SalesByFilmCategory s""",
           countQuery = """
                   SELECT COUNT(s)
                   FROM SalesByFilmCategory s""")
    Page<Tuple> selectSalesShares(Pageable pageable);

    /**
     * Selects every category, with its sales, the running total of the sales down to it, and the share of all sales
     * that total is ({@code SAKILA_SALES_BY_FILM_CATEGORY_A_01}, the book's pick, which orders by
     * {@code s.totalSales} descending, then by {@code s.category}).
     * <p>
     * The running total runs in that order whatever the page's sort is: JPQL has no window function, so the view is
     * joined with itself, and each category {@code s} sums, with a {@code CASE}, the sales of every category {@code t}
     * that sold more, or as much with a name not after it; the share divides that by the sum of all of {@code t}. The
     * rows are grouped by {@code s} itself, which selects it whole. The caller's sort only orders the rows, so give it
     * the exercise's, or the totals do not run down the page. The count query counts the rows of the view.
     *
     * @param pageable the page to return, and its sort, by {@code runningTotal} or {@code runningPercent}, or by an
     *                 attribute of the row through {@code s} ({@code s.category}, {@code s.totalSales}); must not be
     *                 {@code null}.
     * @return a page of the categories, with their running totals, each a tuple of {@code salesByFilmCategory}
     *         ({@link SalesByFilmCategory}), and {@code runningTotal} and {@code runningPercent} ({@link BigDecimal}).
     */
    @Query(value = """
            SELECT s AS salesByFilmCategory,
                   SUM(CASE WHEN t.totalSales > s.totalSales
                                 OR (t.totalSales = s.totalSales AND t.category <= s.category)
                            THEN t.totalSales ELSE 0 END) AS runningTotal,
                   ROUND(100 * SUM(CASE WHEN t.totalSales > s.totalSales
                                             OR (t.totalSales = s.totalSales AND t.category <= s.category)
                                        THEN t.totalSales ELSE 0 END) / SUM(t.totalSales), 2) AS runningPercent
            FROM SalesByFilmCategory s, SalesByFilmCategory t
            GROUP BY s""",
           countQuery = """
                   SELECT COUNT(s)
                   FROM SalesByFilmCategory s""")
    Page<Tuple> selectRunningTotals(Pageable pageable);

    /**
     * Selects every category but the last, with its sales, the category next down, and the gap between their sales
     * ({@code SAKILA_SALES_BY_FILM_CATEGORY_A_02}, this module's own extra, which takes the first three by {@code gap}
     * descending, then by {@code s.category}).
     * <p>
     * JPQL has no {@code LEAD}, so the view is joined with itself: {@code n} is next down from {@code s} when it is
     * below {@code s}, by sales descending and then by name, and {@code NOT EXISTS} a category {@code m} between the
     * two. The last category has none below it, so it is not selected. Both rows are selected whole, and the gap is
     * computed beside them. The count query counts the pairs the same conditions keep.
     *
     * @param pageable the page to return, and its sort, by {@code gap}, or by an attribute of either row through
     *                 {@code s} or {@code n} ({@code s.category}, {@code s.totalSales}, {@code n.category}, ...); must
     *                 not be {@code null}.
     * @return a page of the categories, with the gaps to the next ones down, each a tuple of
     *         {@code salesByFilmCategory} and {@code nextSalesByFilmCategory} ({@link SalesByFilmCategory}), and
     *         {@code gap} ({@link BigDecimal}).
     */
    @Query(value = """
            SELECT s AS salesByFilmCategory, n AS nextSalesByFilmCategory, s.totalSales - n.totalSales AS gap
            FROM SalesByFilmCategory s, SalesByFilmCategory n
            WHERE (n.totalSales < s.totalSales OR (n.totalSales = s.totalSales AND n.category > s.category))
              AND NOT EXISTS (SELECT m
                              FROM SalesByFilmCategory m
                              WHERE (m.totalSales < s.totalSales
                                     OR (m.totalSales = s.totalSales AND m.category > s.category))
                                AND (m.totalSales > n.totalSales
                                     OR (m.totalSales = n.totalSales AND m.category < n.category)))""",
           countQuery = """
                   SELECT COUNT(s)
                   FROM SalesByFilmCategory s, SalesByFilmCategory n
                   WHERE (n.totalSales < s.totalSales OR (n.totalSales = s.totalSales AND n.category > s.category))
                     AND NOT EXISTS (SELECT m
                                     FROM SalesByFilmCategory m
                                     WHERE (m.totalSales < s.totalSales
                                            OR (m.totalSales = s.totalSales AND m.category > s.category))
                                       AND (m.totalSales > n.totalSales
                                            OR (m.totalSales = n.totalSales AND m.category < n.category)))""")
    Page<Tuple> selectGaps(Pageable pageable);
}
