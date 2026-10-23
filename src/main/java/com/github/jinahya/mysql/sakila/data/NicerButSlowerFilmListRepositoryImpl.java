package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.NicerButSlowerFilmList;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The {@link NicerButSlowerFilmListRepository}, over a {@link org.springframework.jdbc.core.simple.JdbcClient}.
 * <p>
 * A {@code public}, {@code @Repository} bean of its own, not part of any Spring Data repository; see
 * {@link _NonEntityRepository} for how it gets registered, and {@link _NonEntityRepositoryImpl} for the client and the
 * exception translation.
 * <p>
 * A row maps into a {@link NicerButSlowerFilmList} through its public constructor, but by an explicit
 * {@link RowMapper} rather than by parameter name, for the reason {@link FilmListRepositoryImpl} gives: the
 * {@link NicerButSlowerFilmList#COLUMN_NAME_RATING rating} column holds the text of MySQL's {@code ENUM}, which is not
 * the name of the {@link Film.Rating} constant, so the mapper converts it by
 * {@link Film.Rating#valueOfColumnValue(String)}.
 * <p>
 * {@link #countDifferingFromFilmList()} reads its statement from
 * {@code NICER_BUT_SLOWER_FILM_LIST_COUNT_DIFFERING_FROM_FILM_LIST.sql} beside this class; the others write theirs in
 * place. A page is selected as {@link FilmListRepositoryImpl} selects one: an {@code ORDER BY} built from the
 * {@link Pageable}'s {@link Sort} through {@link #COLUMNS}, with {@code FID} last, {@code LIMIT ... OFFSET ...} from
 * the page unless the {@code Pageable} is unpaged, and a {@code COUNT(*)} of the same {@code WHERE} for the total, run
 * only when the page needs it.
 *
 * @see NicerButSlowerFilmListRepository
 * @see FilmListRepositoryImpl
 */
@Repository
public class NicerButSlowerFilmListRepositoryImpl
        extends _NonEntityRepositoryImpl<NicerButSlowerFilmList>
        implements NicerButSlowerFilmListRepository {

    /**
     * Maps a row into a {@link NicerButSlowerFilmList}, column by column, converting the rating by its column value.
     */
    static final RowMapper<NicerButSlowerFilmList> ROW_MAPPER = (rs, rowNum) -> new NicerButSlowerFilmList(
            rs.getObject(NicerButSlowerFilmList.COLUMN_NAME_FID, Integer.class),
            rs.getString(NicerButSlowerFilmList.COLUMN_NAME_TITLE),
            rs.getString(NicerButSlowerFilmList.COLUMN_NAME_DESCRIPTION),
            rs.getString(NicerButSlowerFilmList.COLUMN_NAME_CATEGORY),
            rs.getBigDecimal(NicerButSlowerFilmList.COLUMN_NAME_PRICE),
            rs.getObject(NicerButSlowerFilmList.COLUMN_NAME_LENGTH, Integer.class),
            Optional.ofNullable(rs.getString(NicerButSlowerFilmList.COLUMN_NAME_RATING))
                    .map(Film.Rating::valueOfColumnValue)
                    .orElse(null),
            rs.getString(NicerButSlowerFilmList.COLUMN_NAME_ACTORS)
    );

    /**
     * The columns of the view, by the attribute of {@link NicerButSlowerFilmList} each maps to; what a {@link Sort}
     * may name.
     */
    static final Map<String, String> COLUMNS = Map.of(
            NicerButSlowerFilmList.ATTRIBUTE_NAME_FID, NicerButSlowerFilmList.COLUMN_NAME_FID,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_TITLE, NicerButSlowerFilmList.COLUMN_NAME_TITLE,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_DESCRIPTION, NicerButSlowerFilmList.COLUMN_NAME_DESCRIPTION,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_CATEGORY, NicerButSlowerFilmList.COLUMN_NAME_CATEGORY,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_PRICE, NicerButSlowerFilmList.COLUMN_NAME_PRICE,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_LENGTH, NicerButSlowerFilmList.COLUMN_NAME_LENGTH,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_RATING, NicerButSlowerFilmList.COLUMN_NAME_RATING,
            NicerButSlowerFilmList.ATTRIBUTE_NAME_ACTORS, NicerButSlowerFilmList.COLUMN_NAME_ACTORS
    );

    /**
     * The columns the statements select, in the order the view defines them.
     */
    private static final String SELECT = "SELECT " + String.join(", ",
                                                                 NicerButSlowerFilmList.COLUMN_NAME_FID,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_TITLE,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_DESCRIPTION,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_CATEGORY,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_PRICE,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_LENGTH,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_RATING,
                                                                 NicerButSlowerFilmList.COLUMN_NAME_ACTORS)
                                         + " FROM " + NicerButSlowerFilmList.TABLE_NAME;

    /**
     * The statement of {@link #countDifferingFromFilmList()}.
     */
    private static final String SQL_COUNT_DIFFERING_FROM_FILM_LIST =
            sql(NicerButSlowerFilmListRepositoryImpl.class,
                "NICER_BUT_SLOWER_FILM_LIST_COUNT_DIFFERING_FROM_FILM_LIST.sql");

    /**
     * Builds the {@code ORDER BY} clause of the specified sort, with {@code FID} appended as the last key unless the
     * sort names it already, so that the order is total and the pages are stable.
     *
     * @param sort the sort, whose properties are attributes of {@link NicerButSlowerFilmList}.
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
        if (sort.getOrderFor(NicerButSlowerFilmList.ATTRIBUTE_NAME_FID) == null) {
            keys.add(NicerButSlowerFilmList.COLUMN_NAME_FID + ' ' + Sort.Direction.ASC.name());
        }
        return " ORDER BY " + String.join(", ", keys);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    public NicerButSlowerFilmListRepositoryImpl() {
        super(NicerButSlowerFilmList.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Counts the rows that satisfy the specified condition.
     *
     * @param where  the condition, as the body of a {@code WHERE} clause, with named parameters; {@code null} for every
     *               row.
     * @param params the values of the named parameters.
     * @return the number of rows.
     */
    private long count(final @Nullable String where, final Map<String, ?> params) {
        return jdbcClient()
                .sql("SELECT COUNT(*) FROM " + NicerButSlowerFilmList.TABLE_NAME
                     + (where == null ? "" : " WHERE " + where))
                .params(params)
                .query(Long.class)
                .single();
    }

    /**
     * Selects the page of the rows that satisfy the specified condition.
     *
     * @param where    the condition, as the body of a {@code WHERE} clause, with named parameters; {@code null} for
     *                 every row.
     * @param params   the values of the named parameters.
     * @param pageable the page to return, and its sort.
     * @param total    counts the rows that satisfy the condition, when the page needs the total.
     * @return the page.
     */
    private Page<NicerButSlowerFilmList> select(final @Nullable String where, final Map<String, ?> params,
                                                final Pageable pageable, final LongSupplier total) {
        var statement = SELECT + (where == null ? "" : " WHERE " + where) + orderBy(pageable.getSort());
        if (pageable.isPaged()) {
            requirePage(pageable.getOffset(), pageable.getPageSize());
            statement += " LIMIT :limit OFFSET :offset";
        }
        var spec = jdbcClient().sql(statement).params(params);
        if (pageable.isPaged()) {
            spec = spec.param("limit", pageable.getPageSize()).param("offset", pageable.getOffset());
        }
        final var content = spec.query(ROW_MAPPER).list();
        return PageableExecutionUtils.getPage(content, pageable, total);
    }

    @Override
    public Page<NicerButSlowerFilmList> listAll(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        return select(null, Map.of(), pageable, () -> count(null, Map.of()));
    }

    // ----------------------------------------------------------------------------------------------------- exercises
    @Override
    public Page<NicerButSlowerFilmList> listByFid(final Integer fid, final Pageable pageable) {
        Objects.requireNonNull(fid, "fid is null");
        Objects.requireNonNull(pageable, "pageable is null");
        final var where = NicerButSlowerFilmList.COLUMN_NAME_FID + " = :fid";
        final var params = Map.of("fid", fid);
        return select(where, params, pageable, () -> count(where, params));
    }

    @Override
    public long countDifferingFromFilmList() {
        return jdbcClient().sql(SQL_COUNT_DIFFERING_FROM_FILM_LIST)
                .query(Long.class)
                .single();
    }
}
