package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.ActorInfo;
import com.github.jinahya.mysql.sakila.persistence.ActorInfo_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

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
 * The methods below answer the exercises of upstream's {@code EXERCISES_COORDINATES.asciidoc} on the {@code actor_info} view, one
 * method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA-ACTOR_INFO-B-01}, ...), one
 * or two of each level. The exercises are context, not contract: a method that lists rows takes a {@link Pageable},
 * the page and the sort, and returns a {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are
 * the caller's {@code PageRequest}; each method's javadoc says which sort that is. A filter is a {@code default}
 * method, {@code list...} or {@code count...}, which builds a {@link Specification} with the Criteria API and runs it
 * through {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An exercise that shows columns
 * computed in the select, a substring or counts of two tables, cannot be a {@code Specification}, which only
 * filters; such a method, {@code select...}, declares its JPQL with {@link Query} and returns a projection, one of
 * the interfaces declared here, which Spring Data backs with the selected columns, each aliased with the name of the
 * getter it feeds.
 *
 * @see ActorInfo
 * @see ActorInfo_
 */
@Repository
public interface ActorInfoRepository
        extends _Repository<ActorInfo, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * An actor of the view, by id and names, with the head of its {@code film_info}: a projection of
     * {@link #selectFilmInfoHeadByActorId(int, int)}.
     */
    interface FilmInfoHead {

        /**
         * Returns the id of the actor.
         *
         * @return the id of the actor.
         */
        Integer getActorId();

        /**
         * Returns the first name of the actor.
         *
         * @return the first name of the actor.
         */
        String getFirstName();

        /**
         * Returns the last name of the actor.
         *
         * @return the last name of the actor.
         */
        String getLastName();

        /**
         * Returns the first characters of the {@code film_info} of the actor, as many as were asked for.
         *
         * @return the head of the {@code film_info}; {@code null} if the actor's {@code film_info} is {@code null}.
         */
        String getFilmInfo();
    }

    /**
     * How many rows the view has, and how many actors the {@code actor} table has: a projection of
     * {@link #selectRowCounts()}.
     */
    interface RowCounts {

        /**
         * Returns the number of rows of the {@code actor_info} view.
         *
         * @return the number of rows of the view.
         */
        long getActorInfos();

        /**
         * Returns the number of rows of the {@code actor} table.
         *
         * @return the number of actors.
         */
        long getActors();
    }

    /**
     * Selects the row of the actor of the specified id, with its {@code film_info} cut to the specified length
     * ({@code SAKILA-ACTOR_INFO-B-01}, which shows the actor {@code 1} with the first {@code 80} characters; one row,
     * so no sort).
     * <p>
     * The {@code film_info} is long, so the select cuts it with {@code SUBSTRING}, which a {@link Specification}, which
     * only filters, cannot do; hence a {@link Query} and a projection. The lookup is by the id, so it finds one row or
     * none.
     *
     * @param actorId the value of the {@link ActorInfo_#ACTOR_ID} attribute to match.
     * @param length  the number of leading characters of the {@link ActorInfo_#FILM_INFO} attribute to select; must
     *                be positive.
     * @return an optional of the row of the actor whose id is {@code actorId}; empty if there is none.
     */
    @Query("""
            SELECT v.actorId AS actorId, v.firstName AS firstName, v.lastName AS lastName,
                   SUBSTRING(v.filmInfo, 1, :length) AS filmInfo
            FROM ActorInfo v
            WHERE v.actorId = :actorId""")
    Optional<FilmInfoHead> selectFilmInfoHeadByActorId(int actorId, int length);

    /**
     * Counts the actors who appear in at least one film of the specified category, as the {@code film_info} says
     * ({@code SAKILA-ACTOR_INFO-B-02}, which counts the actors of a {@code Horror} film; one number, so no sort).
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
     * Selects how many rows the view has, next to how many actors the {@code actor} table has
     * ({@code SAKILA-ACTOR_INFO-I-01}, which checks that the two are equal; one row, so no sort).
     * <p>
     * The view groups the actors, not the films, so an actor with no film still has a row, and the two counts should
     * be equal. The second count is a subquery in the select, on another entity, which a {@link Specification} of
     * this one cannot select; hence a {@link Query} and a projection. The select aggregates every row of the view
     * into one, so it always has exactly one row, even when the view is empty, and takes no {@link Pageable}.
     *
     * @return the two counts.
     */
    @Query("""
            SELECT COUNT(v) AS actorInfos, (SELECT COUNT(a) FROM Actor a) AS actors
            FROM ActorInfo v""")
    RowCounts selectRowCounts();
}
