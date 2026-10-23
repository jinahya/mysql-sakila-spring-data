package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByStore;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

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
 * {@link #listAll(Pageable)} takes a {@link Pageable}, the page and the sort, and returns a {@link Page}. The sort
 * names the attributes of {@link SalesByStore} ({@link SalesByStore#ATTRIBUTE_NAME_STORE},
 * {@link SalesByStore#ATTRIBUTE_NAME_MANAGER}, {@link SalesByStore#ATTRIBUTE_NAME_TOTAL_SALES}), which the
 * implementation maps to the columns of the view; a property the view has no column for is rejected. The
 * {@link SalesByStore#ATTRIBUTE_NAME_STORE store} is always appended as the last sort key, so that consecutive pages
 * neither repeat nor skip a row whatever the caller sorts by. An unpaged {@code Pageable} returns every row in one
 * page.
 * <p>
 * The methods after {@link #sumTotalSales()} answer the exercises of upstream's {@code EXERCISES.asciidoc} on the
 * {@code sales_by_store} view, one method per exercise, in the order of their ids, each named in its javadoc
 * ({@code SAKILA-SALES_BY_STORE-B-01}, ...). The view has no entity, so every one of them is native-only: it runs the
 * exercise's SQL as {@code EXERCISES.asciidoc} has it, its {@code ORDER BY} included, from a {@code .sql} resource
 * beside the implementation. A method whose statement selects whole rows of the view, with {@code v.*}, returns them
 * as {@link SalesByStore}s, next to the value it computes, if any, in one of the records nested here; one that sums
 * returns a single value, or its columns as such a record.
 *
 * @see SalesByStore
 * @see SalesByStoreRepositoryImpl
 */
public interface SalesByStoreRepository
        extends _NonEntityRepository<SalesByStore> {

    /**
     * The sum of the stores' sales next to the sum of the payments which have a rental.
     *
     * @param byStore     the sum of the {@code total_sales} of the view; {@code by_store}.
     * @param withARental the sum of the {@code amount} of the payments with a rental; {@code with_a_rental}.
     */
    record ByStoreAndWithARental(BigDecimal byStore, BigDecimal withARental) {

    }

    /**
     * A row of the view next to the sum of the payments its manager took, at whichever store.
     *
     * @param sales          the row of the view: the {@code store}, its {@code manager} and its {@code total_sales}.
     * @param takenByManager the sum of the {@code amount} of the payments the manager took; {@code taken_by_manager}.
     */
    record SalesAndTakenByManager(SalesByStore sales, BigDecimal takenByManager) {

    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Lists every row of the view.
     * <p>
     * Not an exercise; the view's plain listing, which the tests select their fixture rows with and page through.
     * {@link #listSales()} lists the same rows, as {@code SAKILA-SALES_BY_STORE-B-01} asks.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the view, one per store.
     */
    Page<SalesByStore> listAll(Pageable pageable);

    /**
     * Sums the {@link SalesByStore#COLUMN_NAME_TOTAL_SALES total_sales} of every row of the view.
     * <p>
     * Not an exercise; {@link #sumByStoreAndWithARental()} sums the same, next to what it is checked against.
     *
     * @return the sum of the {@code total_sales} of the stores; {@code 0} when the view has no row.
     */
    BigDecimal sumTotalSales();

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Lists every store's sales ({@code SAKILA-SALES_BY_STORE-B-01}; native-only; no sort, the rows in the order the
     * view defines, by country and then by city, which the {@code store} column, {@code city,country}, does not
     * reproduce), selecting the whole rows.
     *
     * @return the rows of the view, one per store.
     */
    List<SalesByStore> listSales();

    /**
     * Selects how far apart the stores' sales are ({@code SAKILA-SALES_BY_STORE-B-02}; native-only; no sort, a single
     * value).
     *
     * @return the largest {@code total_sales} less the smallest; {@code difference}; {@code null} when the view has no
     *         row.
     */
    @Nullable
    BigDecimal selectSalesDifference();

    /**
     * Sums the stores' sales next to every payment with a rental ({@code SAKILA-SALES_BY_STORE-I-01}; native-only; no
     * sort, a single row), to check that the two add up.
     * <p>
     * The view reaches a store through the payment's rental, so a payment without one cannot be in it; the two sums are
     * the same.
     *
     * @return the sum of the stores' sales and the sum of the payments which have a rental.
     */
    ByStoreAndWithARental sumByStoreAndWithARental();

    /**
     * Lists each store's sales, next to what its manager took ({@code SAKILA-SALES_BY_STORE-I-02}; native-only; sorted
     * by {@code store}), selecting the whole rows next to the computed {@code taken_by_manager}.
     * <p>
     * The view has no key, so the rows are grouped by all three of its columns. The manager is joined to
     * {@code staff} by the full name the view writes, and the payments of that staff member are summed, at whichever
     * store they were taken; so the two sums differ, while adding up to the same total.
     *
     * @return a row per store, the row of the view next to the sum of the payments its manager took, by store.
     */
    List<SalesAndTakenByManager> listSalesAndTakenByManager();
}
