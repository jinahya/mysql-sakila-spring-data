package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmList;
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
 * The {@link FilmListRepository}, over a {@link org.springframework.jdbc.core.simple.JdbcClient}.
 * <p>
 * A {@code public}, {@code @Repository} bean of its own, not part of any Spring Data repository; see
 * {@link _NonEntityRepository} for how it gets registered, and {@link _NonEntityRepositoryImpl} for the client and the
 * exception translation.
 * <p>
 * A row maps into a {@link FilmList} through its public constructor, but by an explicit {@link RowMapper} rather than
 * by parameter name, as the base describes: the {@link FilmList#COLUMN_NAME_RATING rating} column holds the text of
 * MySQL's {@code ENUM} ({@code PG-13}, {@code NC-17}), which is not the name of the {@link Film.Rating} constant, and
 * Spring's conversion of a {@code String} to an enum goes by the name; the mapper goes by
 * {@link Film.Rating#valueOfColumnValue(String)} instead.
 * <p>
 * {@link #countAll()} reads its statement from {@code FILM_LIST_COUNT_ALL.sql} beside this class; the others write
 * theirs in place. A page is selected with an {@code ORDER BY} built from the {@link Pageable}'s {@link Sort}, each
 * property mapped to a column of the view through {@link #COLUMNS}, and {@code FID} last, and with
 * {@code LIMIT ... OFFSET ...} from the page, unless the {@code Pageable} is unpaged; the total comes from a
 * {@code COUNT(*)} of the same {@code WHERE}, run only when the page needs it.
 *
 * @see FilmListRepository
 */
@Repository
public class FilmListRepositoryImpl
        extends _NonEntityRepositoryImpl<FilmList>
        implements FilmListRepository {

    /**
     * Maps a row into a {@link FilmList}, column by column, converting the rating by its column value.
     */
    static final RowMapper<FilmList> ROW_MAPPER = (rs, rowNum) -> new FilmList(
            rs.getObject(FilmList.COLUMN_NAME_FID, Integer.class),
            rs.getString(FilmList.COLUMN_NAME_TITLE),
            rs.getString(FilmList.COLUMN_NAME_DESCRIPTION),
            rs.getString(FilmList.COLUMN_NAME_CATEGORY),
            rs.getBigDecimal(FilmList.COLUMN_NAME_PRICE),
            rs.getObject(FilmList.COLUMN_NAME_LENGTH, Integer.class),
            Optional.ofNullable(rs.getString(FilmList.COLUMN_NAME_RATING))
                    .map(Film.Rating::valueOfColumnValue)
                    .orElse(null),
            rs.getString(FilmList.COLUMN_NAME_ACTORS)
    );

    /**
     * The columns of the view, by the attribute of {@link FilmList} each maps to; what a {@link Sort} may name.
     */
    static final Map<String, String> COLUMNS = Map.of(
            FilmList.ATTRIBUTE_NAME_FID, FilmList.COLUMN_NAME_FID,
            FilmList.ATTRIBUTE_NAME_TITLE, FilmList.COLUMN_NAME_TITLE,
            FilmList.ATTRIBUTE_NAME_DESCRIPTION, FilmList.COLUMN_NAME_DESCRIPTION,
            FilmList.ATTRIBUTE_NAME_CATEGORY, FilmList.COLUMN_NAME_CATEGORY,
            FilmList.ATTRIBUTE_NAME_PRICE, FilmList.COLUMN_NAME_PRICE,
            FilmList.ATTRIBUTE_NAME_LENGTH, FilmList.COLUMN_NAME_LENGTH,
            FilmList.ATTRIBUTE_NAME_RATING, FilmList.COLUMN_NAME_RATING,
            FilmList.ATTRIBUTE_NAME_ACTORS, FilmList.COLUMN_NAME_ACTORS
    );

    /**
     * The columns the statements select, in the order the view defines them.
     */
    private static final String SELECT = "SELECT " + String.join(", ",
                                                                 FilmList.COLUMN_NAME_FID,
                                                                 FilmList.COLUMN_NAME_TITLE,
                                                                 FilmList.COLUMN_NAME_DESCRIPTION,
                                                                 FilmList.COLUMN_NAME_CATEGORY,
                                                                 FilmList.COLUMN_NAME_PRICE,
                                                                 FilmList.COLUMN_NAME_LENGTH,
                                                                 FilmList.COLUMN_NAME_RATING,
                                                                 FilmList.COLUMN_NAME_ACTORS)
                                         + " FROM " + FilmList.TABLE_NAME;

    /**
     * The statement of {@link #countAll()}.
     */
    private static final String SQL_COUNT_ALL = sql(FilmListRepositoryImpl.class, "FILM_LIST_COUNT_ALL.sql");

    /**
     * Builds the {@code ORDER BY} clause of the specified sort, with {@code FID} appended as the last key unless the
     * sort names it already, so that the order is total and the pages are stable.
     *
     * @param sort the sort, whose properties are attributes of {@link FilmList}.
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
        if (sort.getOrderFor(FilmList.ATTRIBUTE_NAME_FID) == null) {
            keys.add(FilmList.COLUMN_NAME_FID + ' ' + Sort.Direction.ASC.name());
        }
        return " ORDER BY " + String.join(", ", keys);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    public FilmListRepositoryImpl() {
        super(FilmList.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

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
    private Page<FilmList> select(final @Nullable String where, final Map<String, ?> params, final Pageable pageable,
                                  final LongSupplier total) {
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
    public Page<FilmList> listAll(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        return select(null, Map.of(), pageable, this::countAll);
    }

    // ----------------------------------------------------------------------------------------------------- exercises
    @Override
    public long countByCategory(final String category) {
        Objects.requireNonNull(category, "category is null");
        return jdbcClient().sql("SELECT COUNT(*) FROM " + FilmList.TABLE_NAME
                                + " WHERE " + FilmList.COLUMN_NAME_CATEGORY + " = :category")
                .param("category", category)
                .query(Long.class)
                .single();
    }

    @Override
    public Page<FilmList> listByFid(final Integer fid, final Pageable pageable) {
        Objects.requireNonNull(fid, "fid is null");
        Objects.requireNonNull(pageable, "pageable is null");
        final var where = FilmList.COLUMN_NAME_FID + " = :fid";
        final var params = Map.of("fid", fid);
        return select(where, params, pageable, () -> jdbcClient()
                .sql("SELECT COUNT(*) FROM " + FilmList.TABLE_NAME + " WHERE " + where)
                .params(params)
                .query(Long.class)
                .single());
    }

    @Override
    public long countAll() {
        return jdbcClient().sql(SQL_COUNT_ALL)
                .query(Long.class)
                .single();
    }
}
