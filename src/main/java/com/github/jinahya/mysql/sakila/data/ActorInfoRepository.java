package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.ActorInfo;
import com.github.jinahya.mysql.sakila.persistence.ActorInfo_;
import com.github.jinahya.mysql.sakila.persistence.Actor_;
import com.github.jinahya.mysql.sakila.persistence.Category;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A repository for {@link ActorInfo}, the {@code actor_info} view of the Sakila database.
 * <p>
 * The view is read-only: it groups, for each actor of the {@code actor} table, the films the actor appears in into one
 * {@code film_info} string, by category. A row is identified by {@link ActorInfo_#ACTOR_ID}, the id of the actor it is
 * about, which is the only column upstream maps as insertable, since Jakarta Persistence requires an {@code @Id} that
 * is not read-only; every other column is {@code insertable = false, updatable = false}. Nothing is ever saved through
 * this repository: a view accepts no insert, and the Sakila database is only read. {@link ActorInfo#equals(Object)}
 * compares ids, and only once they are assigned.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.like(root.get(ActorInfo_.filmInfo), pattern)}.
 * <p>
 * {@link ActorInfo} declares named queries too ({@code ActorInfo.selectOneByActorId},
 * {@code ActorInfo.selectListOrderByActorIdAsc}, {@code ActorInfo.selectListOrderByActorIdAscActorIdGt} and
 * {@code ActorInfo.countAll}), but no method here runs them yet.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code actor_info} view, one
 * method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA_ACTOR_INFO_B_01}, ...), with
 * its role: the book's pick, this module's own extra, native-only, or not a pick (an exercise this module answered
 * before the picks were made). The exercises are context, not contract. A Basic exercise returns a {@link List} (the
 * book's pick) or a {@link Stream} (this module's own extra), with the order the exercise asks for in the query, and
 * no {@link Pageable}. An Intermediate or Advanced one takes a {@link Pageable}, the page and the sort, and returns a
 * {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are the caller's {@code PageRequest}; each
 * method's javadoc says which sort that is. A filter is a {@code default} method, {@code list...} or
 * {@code count...}, which builds a {@link Specification} with the Criteria API and runs it through
 * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An exercise that shows columns computed in
 * the select, a substring or counts of two tables, cannot be a {@code Specification}, which only filters; such a
 * method, {@code select...}, declares its JPQL with {@link Query}. Such a query selects entities, not their
 * columns: a row of the view is selected whole ({@code SELECT v AS actorInfo}); what is computed, a substring, a
 * count, is a column of its own beside it. A result that is a single entity is that entity; any other comes back as a
 * {@link jakarta.persistence.Tuple}, each element read by its alias, with its type
 * ({@code tuple.get("actorInfo", ActorInfo.class)}, {@code tuple.get("actors", Long.class)}). The {@code Pageable}
 * sorts a computed column by its alias, and an attribute of a selected entity by its path from the query's
 * identification variable ({@code Sort.by("v.actorId")}); each method's javadoc names its aliases and identification
 * variables. A query whose select has no entity at all, only counts, still selects those counts. A native-only
 * exercise, grouped on a computed value, is a {@link NativeQuery} with the exercise's SQL.
 *
 * @see ActorInfo
 * @see ActorInfo_
 */
@Repository
public interface ActorInfoRepository
        extends _Repository<ActorInfo, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Selects the row of the actor of the specified id, with its {@code film_info} cut to the specified length, as a
     * stream ({@code SAKILA_ACTOR_INFO_B_01}, this module's own extra, which shows the actor {@code 1} with the first
     * {@code 80} characters, and orders by {@code v.actorId}).
     * <p>
     * The {@code film_info} is long, so the select cuts it with {@code SUBSTRING}, which a {@link Specification}, which
     * only filters, cannot do; hence a {@link Query} and a tuple, of the row of the view whole and, beside it, its
     * {@code film_info} cut. The lookup is by the id, so the stream yields
     * one row or none; the {@code ORDER BY} is the exercise's all the same. The stream is to be consumed inside a
     * transaction the caller opened, and closed, in a try-with-resources; outside one, Spring Data refuses the call
     * with an {@link org.springframework.dao.InvalidDataAccessApiUsageException}, before any query runs.
     *
     * @param actorId the value of the {@link ActorInfo_#ACTOR_ID} attribute to match.
     * @param length  the number of leading characters of the {@link ActorInfo_#FILM_INFO} attribute to select; must
     *                be positive.
     * @return a stream of the row of the actor whose id is {@code actorId}, to be closed by the caller; empty if there
     *         is none; the row is a tuple of {@code actorInfo} ({@link ActorInfo}) and {@code filmInfo}
     *         ({@link String}), the actor's {@code film_info} cut to {@code length} characters, and {@code null} if
     *         the actor's {@code film_info} is.
     */
    @Query("""
            SELECT v AS actorInfo, SUBSTRING(v.filmInfo, 1, :length) AS filmInfo
            FROM ActorInfo v
            WHERE v.actorId = :actorId
            ORDER BY v.actorId""")
    Stream<Tuple> selectFilmInfoHeadByActorId(int actorId, int length);

    /**
     * Counts the actors who appear in at least one film of the specified category, as the {@code film_info} says
     * ({@code SAKILA_ACTOR_INFO_B_02}, not a pick, which counts the actors of a {@code Horror} film; one number, so no
     * sort).
     * <p>
     * Each category in the {@code film_info} string is written as its name followed by a colon, so a
     * {@link Specification} of one {@code LIKE}, on {@code %<category>:%}, finds the actors without joining any table.
     * The pattern is built here, so the caller passes the category's name alone.
     *
     * @param category the name of the category, as the {@link ActorInfo_#FILM_INFO} attribute writes it; must not be
     *                 {@code null}.
     * @return the number of actors whose {@code film_info} names a film of {@code category}.
     */
    default long countByFilmInfoCategory(final String category) {
        Objects.requireNonNull(category, "category is null");
        final Specification<ActorInfo> specification =
                (r, q, b) -> b.like(r.get(ActorInfo_.filmInfo), '%' + category + ":%");
        return count(specification);
    }

    /**
     * Finds the actors whose {@code film_info} names the specified film ({@code SAKILA_ACTOR_INFO_B_03}, the book's
     * pick, which finds the actors of {@code ACADEMY DINOSAUR}, and orders by {@link ActorInfo_#ACTOR_ID}).
     * <p>
     * The query, and its order, are derived from the method name. {@code Containing} is a {@code LIKE} with a
     * {@code %} added on each side by Spring Data, which escapes any wildcard in the title, so the caller passes the
     * title alone; no table but the view is read.
     *
     * @param title the title of the film, as the {@link ActorInfo_#FILM_INFO} attribute writes it; must not be
     *              {@code null}.
     * @return a list of the rows whose {@code film_info} contains {@code title}, by id; empty if there is none.
     */
    List<ActorInfo> findAllByFilmInfoContainingOrderByActorIdAsc(String title);

    /**
     * Selects how many rows the view has, next to how many actors the {@code actor} table has
     * ({@code SAKILA_ACTOR_INFO_I_01}, this module's own extra, which checks that the two are equal; one row, so no
     * sort).
     * <p>
     * The view groups the actors, not the films, so an actor with no film still has a row, and the two counts should
     * be equal. The second count is a subquery in the select, on another entity, which a {@link Specification} of
     * this one cannot select; hence a {@link Query} and a tuple. The select aggregates every row of the view
     * into one, so it always has exactly one row, even when the view is empty; the page holds that row, and the
     * count query, which Spring Data cannot derive from an aggregated select, says so: it counts the rows of the same
     * aggregate, as a subquery in its {@code FROM}.
     *
     * @param pageable the page to return; must not be {@code null}.
     * @return a page of the one row of the two counts, a tuple of {@code actorInfos} and {@code actors}
     *         ({@link Long}).
     */
    @Query(value = """
            SELECT COUNT(v) AS actorInfos, (SELECT COUNT(a) FROM Actor a) AS actors
            FROM ActorInfo v""",
           countQuery = """
                   SELECT COUNT(*)
                   FROM (SELECT COUNT(v) AS actorInfos
                         FROM ActorInfo v) c""")
    Page<Tuple> selectRowCounts(Pageable pageable);

    /**
     * Lists the actors whose {@code film_info} covers every category ({@code SAKILA_ACTOR_INFO_I_02}, the book's pick,
     * which orders by {@link ActorInfo_#ACTOR_ID}).
     * <p>
     * Each category in the {@code film_info} is followed by one colon, so the length of the string less the length of
     * the string with the colons removed is the number of categories it names. A {@link Specification} compares that
     * with a subquery counting the {@code category} table; a row whose {@code film_info} is {@code null}, an actor of
     * no film, compares as unknown and is not listed.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows whose {@code film_info} names as many categories as there are.
     */
    default Page<ActorInfo> listCoveringEveryCategory(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        final Specification<ActorInfo> specification = (r, q, b) -> {
            final var filmInfo = r.get(ActorInfo_.filmInfo);
            final var categoriesOfTheActor = b.diff(b.length(filmInfo), b.length(b.replace(filmInfo, ":", "")));
            final var categories = Objects.requireNonNull(q).subquery(Long.class);
            categories.select(b.count(categories.from(Category.class)));
            return b.equal(categoriesOfTheActor, categories);
        };
        return findAll(specification, pageable);
    }

    /**
     * Selects how many actors cover how many categories, as their {@code film_info} says
     * ({@code SAKILA_ACTOR_INFO_I_03}, native-only, which orders by {@code categories} descending).
     * <p>
     * The categories of a row are counted as in {@link #listCoveringEveryCategory(Pageable)}, and the rows are grouped
     * by that number: the exercise's SQL groups on the alias of a computed value, which upstream answers in SQL only,
     * so this is a {@link NativeQuery} of that SQL, the order with it, and runs on the Sakila database only. A row
     * whose {@code film_info} is {@code null} counts in a group of its own, a {@code null} number of categories, as the
     * SQL has it.
     *
     * @return a list of the numbers of categories, each with its number of actors, the most categories first, each a
     *         tuple of {@code categories} and {@code actors}, numbers as the JDBC driver types them (on MySQL, a
     *         {@link Long} each); {@code categories} is {@code null} for the actors whose {@code film_info} is
     *         {@code null}.
     */
    @NativeQuery("""
            SELECT CHAR_LENGTH(film_info) - CHAR_LENGTH(REPLACE(film_info, ':', '')) AS categories, COUNT(*) AS actors
            FROM actor_info
            GROUP BY categories
            ORDER BY categories DESC""")
    List<Tuple> selectActorCountsByCategoryCount();

    /**
     * Lists the actors whose {@code film_info} disagrees with the tables on the number of categories
     * ({@code SAKILA_ACTOR_INFO_A_01}, the book's pick, which orders by {@link ActorInfo_#ACTOR_ID}).
     * <p>
     * The categories the {@code film_info} names are counted as in {@link #listCoveringEveryCategory(Pageable)}; the
     * categories of the actor's films are counted by a correlated subquery, from the actor's {@code film_actor} links
     * through each film to its {@code film_category} links, distinct by category. A {@link Specification} keeps the
     * rows where the two differ. On the sample data, none does.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows whose {@code film_info} names a number of categories other than the tables say.
     */
    default Page<ActorInfo> listDisagreeingOnCategories(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        final Specification<ActorInfo> specification = (r, q, b) -> {
            final var filmInfo = r.get(ActorInfo_.filmInfo);
            final var categoriesOfTheView = b.diff(b.length(filmInfo), b.length(b.replace(filmInfo, ":", "")));
            final var categoriesOfTheTables = Objects.requireNonNull(q).subquery(Long.class);
            final var fa = categoriesOfTheTables.from(FilmActor.class);
            final var fc = fa.join(FilmActor_.film).join(Film_.filmCategories);
            categoriesOfTheTables.select(b.countDistinct(fc.get(FilmCategory_.category)))
                    .where(b.equal(fa.get(FilmActor_.actor).get(Actor_.actorId), r.get(ActorInfo_.actorId)));
            return b.notEqual(categoriesOfTheView, categoriesOfTheTables);
        };
        return findAll(specification, pageable);
    }
}
