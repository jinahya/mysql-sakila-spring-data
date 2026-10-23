package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * A repository for {@link SalesByStore}, the {@code sales_by_store} view of the Sakila database.
 * <p>
 * The view sums the payments of each store, and names the store by its city and country, and its manager. No column of
 * it identifies a row: it groups by the store's id, which it does not select, and two stores may share a city and a
 * country, so upstream maps it without {@code @Entity}. So this is not a Spring Data repository, and declares no
 * {@code findById}, {@code save} or {@code delete}; see {@link _NonEntityRepository}. It is implemented by hand, in
 * {@link SalesByStoreRepositoryImpl}, over a {@code JdbcClient}, and nothing registers that bean on its own: a consumer
 * component-scans this package or {@code @Import}s the class, and injects it by this interface.
 * <p>
 * A method that returns rows takes a {@link Pageable}, the page and the sort, and returns a {@link Page}. The sort
 * names the attributes of {@link SalesByStore} ({@link SalesByStore#ATTRIBUTE_NAME_STORE},
 * {@link SalesByStore#ATTRIBUTE_NAME_MANAGER}, {@link SalesByStore#ATTRIBUTE_NAME_TOTAL_SALES}), which the
 * implementation maps to the columns of the view; a property the view has no column for is rejected. The
 * {@link SalesByStore#ATTRIBUTE_NAME_STORE store} is always appended as the last sort key, so that consecutive pages
 * neither repeat nor skip a row whatever the caller sorts by. An unpaged {@code Pageable} returns every row in one
 * page.
 * <p>
 * The methods answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code sales_by_store} view, one
 * method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA-SALES_BY_STORE-B-01}, ...).
 * The exercises are context, not contract: an exercise that shows rows is a {@code list...} method, which leaves the
 * order, and the {@code LIMIT}, to the caller's {@code PageRequest}, and an exercise that sums is a {@code sum...}
 * method.
 *
 * @see SalesByStore
 * @see SalesByStoreRepositoryImpl
 */
public interface SalesByStoreRepository
        extends _NonEntityRepository<SalesByStore> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Lists every row of the view ({@code SAKILA-SALES_BY_STORE-B-01}, which shows every store's sales: the
     * {@code store}, the {@code manager} and the {@code total_sales}, in the order the view defines, by country and
     * then by city, which the {@code store} column, {@code city,country}, does not reproduce; sort by
     * {@link SalesByStore#ATTRIBUTE_NAME_STORE} for a stable order).
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the view, one per store.
     */
    Page<SalesByStore> listAll(Pageable pageable);

    /**
     * Sums the {@link SalesByStore#COLUMN_NAME_TOTAL_SALES total_sales} of every row of the view
     * ({@code SAKILA-SALES_BY_STORE-I-01}, which checks that the stores' sales add up to every payment with a rental).
     * <p>
     * The view reaches a store through the payment's rental, so a payment without one cannot be in it. The exercise
     * shows this sum next to the sum of the {@code amount} of the payments which have a rental, which the caller sums
     * for itself, through the repository of {@code payment}; the two are the same.
     *
     * @return the sum of the {@code total_sales} of the stores; {@code 0} when the view has no row.
     */
    BigDecimal sumTotalSales();
}
