package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory;
import com.github.jinahya.mysql.sakila.persistence.SalesByFilmCategory_;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
 * ({@code SAKILA-SALES_BY_FILM_CATEGORY-B-01}, ...), one of each level, which is all the section has. The exercises
 * are context, not contract: a method that lists rows takes a {@link Pageable}, the page and the sort, and returns a
 * {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are the caller's {@code PageRequest}; each
 * method's javadoc says which sort that is. An exercise that shows rows is a {@code default} method, {@code list...},
 * which builds a {@link Specification} with the Criteria API and runs it through
 * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An exercise that shows sums of two tables
 * cannot be a {@code Specification}, which only filters; such a method, {@code select...}, declares its JPQL with
 * {@link Query} and returns a projection, one of the interfaces declared here, which Spring Data backs with the
 * selected columns, each aliased with the name of the getter it feeds.
 *
 * @see SalesByFilmCategory
 * @see SalesByFilmCategory_
 */
@Repository
public interface SalesByFilmCategoryRepository
        extends _Repository<SalesByFilmCategory, String> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * The sum of the view's {@code total_sales} over all categories, and the sum of the {@code amount} of all
     * payments: a projection of {@link #selectSalesTotals()}.
     */
    interface SalesTotals {

        /**
         * Returns the sum of the {@code total_sales} of every row of the view.
         *
         * @return the sum of the sales by category; {@code null} if the view has no row, since the sum of nothing is
         *         {@code null}.
         */
        @Nullable BigDecimal getByCategory();

        /**
         * Returns the sum of the {@code amount} of every row of the {@code payment} table.
         *
         * @return the sum of all payments; {@code null} if there is no payment.
         */
        @Nullable BigDecimal getAllPayments();
    }

    /**
     * Lists every row of the view ({@code SAKILA-SALES_BY_FILM_CATEGORY-B-01}, which takes the first three by
     * {@link SalesByFilmCategory_#TOTAL_SALES} descending, the categories which sold the most).
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
     * Selects the sum of the view's {@code total_sales} over all categories, next to the sum of the {@code amount} of
     * all payments ({@code SAKILA-SALES_BY_FILM_CATEGORY-I-01}, which checks that the two are equal; one row, so no
     * sort).
     * <p>
     * The two are equal only if every payment reaches exactly one category, through its rental, copy and film. The
     * second sum is a subquery in the select, on another entity, which a {@link Specification} of this one cannot
     * select; hence a {@link Query} and a projection. The select aggregates every row of the view into one, so it
     * always has exactly one row, even when the view is empty, and takes no {@link Pageable}; the sums of no rows are
     * {@code null}, not zero.
     *
     * @return the two sums.
     */
    @Query("""
            SELECT SUM(v.totalSales) AS byCategory, (SELECT SUM(p.amount) FROM Payment p) AS allPayments
            FROM SalesByFilmCategory v""")
    SalesTotals selectSalesTotals();
}
