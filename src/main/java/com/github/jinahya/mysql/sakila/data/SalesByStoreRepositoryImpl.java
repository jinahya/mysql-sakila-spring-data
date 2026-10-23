package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.SalesByStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * The {@link SalesByStoreRepository}, over a {@link org.springframework.jdbc.core.simple.JdbcClient}.
 * <p>
 * A {@code public}, {@code @Repository} bean of its own, not part of any Spring Data repository; see
 * {@link _NonEntityRepository} for how it gets registered, and {@link _NonEntityRepositoryImpl} for the client and the
 * exception translation.
 * <p>
 * A row maps into a {@link SalesByStore} through its public constructor, by parameter name, as the base describes:
 * Spring's {@code SimplePropertyRowMapper} matches {@code store} and {@code manager} to the parameters of those names,
 * and {@code total_sales} to {@code totalSales}, by the underscore convention.
 * <p>
 * {@link #sumTotalSales()} reads its statement from {@code SALES_BY_STORE_SUM_TOTAL_SALES.sql} beside this class;
 * {@link #listAll(Pageable)} writes its own in place. The page is selected with an {@code ORDER BY} built from the
 * {@link Pageable}'s {@link Sort}, each property mapped to a column of the view through {@link #COLUMNS}, and
 * {@code store} last, and with {@code LIMIT ... OFFSET ...} from the page, unless the {@code Pageable} is unpaged;
 * the total comes from a {@code COUNT(*)} of the view, run only when the page needs it.
 *
 * @see SalesByStoreRepository
 */
@Repository
public class SalesByStoreRepositoryImpl
        extends _NonEntityRepositoryImpl<SalesByStore>
        implements SalesByStoreRepository {

    /**
     * The columns of the view, by the attribute of {@link SalesByStore} each maps to; what a {@link Sort} may name.
     */
    static final Map<String, String> COLUMNS = Map.of(
            SalesByStore.ATTRIBUTE_NAME_STORE, SalesByStore.COLUMN_NAME_STORE,
            SalesByStore.ATTRIBUTE_NAME_MANAGER, SalesByStore.COLUMN_NAME_MANAGER,
            SalesByStore.ATTRIBUTE_NAME_TOTAL_SALES, SalesByStore.COLUMN_NAME_TOTAL_SALES
    );

    /**
     * The statement of {@link #sumTotalSales()}.
     */
    private static final String SQL_SUM_TOTAL_SALES =
            sql(SalesByStoreRepositoryImpl.class, "SALES_BY_STORE_SUM_TOTAL_SALES.sql");

    /**
     * Builds the {@code ORDER BY} clause of the specified sort, with {@code store} appended as the last key unless the
     * sort names it already, so that the order is total and the pages are stable.
     *
     * @param sort the sort, whose properties are attributes of {@link SalesByStore}.
     * @return the clause, with its leading space.
     * @throws IllegalArgumentException if {@code sort} names a property the view has no column for.
     */
    private static String orderBy(final Sort sort) {
        final var keys = new ArrayList<String>();
        for (final var order : sort) {
            final var column = COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new IllegalArgumentException("unknown property: " + order.getProperty());
            }
            keys.add(column + ' ' + order.getDirection().name());
        }
        if (sort.getOrderFor(SalesByStore.ATTRIBUTE_NAME_STORE) == null) {
            keys.add(SalesByStore.COLUMN_NAME_STORE + ' ' + Sort.Direction.ASC.name());
        }
        return " ORDER BY " + String.join(", ", keys);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    public SalesByStoreRepositoryImpl() {
        super(SalesByStore.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Counts the rows of the view.
     *
     * @return the number of rows, which is the number of stores with a payment.
     */
    private long countAll() {
        return jdbcClient().sql("SELECT COUNT(*) FROM " + SalesByStore.TABLE_NAME)
                .query(Long.class)
                .single();
    }

    // ----------------------------------------------------------------------------------------------------- exercises
    @Override
    public Page<SalesByStore> listAll(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        var statement = "SELECT " + String.join(", ",
                                                SalesByStore.COLUMN_NAME_STORE,
                                                SalesByStore.COLUMN_NAME_MANAGER,
                                                SalesByStore.COLUMN_NAME_TOTAL_SALES)
                        + " FROM " + SalesByStore.TABLE_NAME + orderBy(pageable.getSort());
        if (pageable.isPaged()) {
            requirePage(pageable.getOffset(), pageable.getPageSize());
            statement += " LIMIT :limit OFFSET :offset";
        }
        var spec = jdbcClient().sql(statement);
        if (pageable.isPaged()) {
            spec = spec.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
        }
        final var content = spec.query(targetClass).list();
        return PageableExecutionUtils.getPage(content, pageable, this::countAll);
    }

    @Override
    public BigDecimal sumTotalSales() {
        return jdbcClient().sql(SQL_SUM_TOTAL_SALES)
                .query(BigDecimal.class)
                .single();
    }
}
