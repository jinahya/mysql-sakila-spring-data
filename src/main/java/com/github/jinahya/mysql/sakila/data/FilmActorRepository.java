package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Actor;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActorId;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * A repository for {@link FilmActor}, the {@code film_actor} table of the Sakila database, which links an actor to a
 * film.
 * <p>
 * The id is a {@link FilmActorId}, an {@code @EmbeddedId} of the actor's id and the film's id, which the two
 * {@code @MapsId} associations {@link FilmActor_#ACTOR} and {@link FilmActor_#FILM} fill in, so a link is constructed
 * from its actor and film, with {@link FilmActor#of(com.github.jinahya.mysql.sakila.persistence.Actor,
 * com.github.jinahya.mysql.sakila.persistence.Film)}, and its id is known before it is saved. The table has no other
 * column but {@code last_update}.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.equal(root.get(FilmActor_.film), film)}; the ids of the actor and the film
 * are reached through the id, {@code root.get(FilmActor_.id).get(FilmActorId_.actorId)}.
 * <p>
 * {@link FilmActor} declares named queries too ({@code FilmActor.selectOneByActorIdAndFilmId},
 * {@code FilmActor.selectListByActorOrderByFilmIdAsc}, {@code FilmActor.countByFilm}, ...), but no method here runs
 * them yet. One that does is named after the query, and binds its parameters with {@code @Param}.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code film_actor} table,
 * one method per exercise, in the order of the exercises' ids, each named in its javadoc by the id
 * ({@code SAKILA_FILM_ACTOR_I_01}, ...) and by its role: the book's pick, this module's own extra, or native-only.
 * The exercises are context, not contract: a method that shows rows takes a {@link Pageable}, the page and the sort,
 * and returns a {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are the caller's
 * {@code PageRequest}; each method's javadoc says which sort that is. Every exercise of this table groups and counts
 * the links, which a {@link Specification}, which only filters, cannot do; so each method, {@code select...},
 * declares its JPQL with {@link Query}, with a count query of its own since Spring Data cannot derive one from a
 * grouped select. Such a query selects entities, not their columns: a film is selected whole ({@code SELECT f AS
 * film}), and grouped by itself ({@code GROUP BY f}), as is an actor of a pair ({@code SELECT a1 AS actor1, a2 AS
 * actor2}); what is computed, a count, is a column of its own beside it. A result that is a single entity is that
 * entity; any other comes back as a {@link jakarta.persistence.Tuple}, each element read by its alias, with its type
 * ({@code tuple.get("film", Film.class)}, {@code tuple.get("actors", Long.class)}). The {@code Pageable} sorts a
 * computed column by its alias ({@code Sort.by(DESC, "actors")}), and an attribute of a selected entity by its path
 * from the query's identification variable ({@code Sort.by("f.filmId")}); each method's javadoc names its aliases and
 * identification variables. The exercise that gives a single value, an average, returns that value, not a page. A
 * native query groups by a value, the number of actors of a film, and selects that value. An exercise upstream
 * flags native-only is a {@link NativeQuery} with the exercise's SQL, returning a {@link List} in the SQL's own order.
 *
 * @see FilmActor
 * @see FilmActor_
 */
@Repository
public interface FilmActorRepository
        extends _Repository<FilmActor, FilmActorId> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Selects the films which have at least one actor, with how many ({@code SAKILA_FILM_ACTOR_I_01}, this module's
     * own extra, which takes the first five by {@code actors} descending, then by {@code f.filmId}, so that the five
     * are always the same five).
     * <p>
     * The links are joined with their film, grouped by the film itself, which selects the film whole, and counted; a
     * film with no link is not in the page. The count query counts the distinct films of the links.
     *
     * @param pageable the page to return, and its sort, by {@code actors}, or by an attribute of the film through
     *                 {@code f} ({@code f.filmId}, {@code f.title}, ...); must not be {@code null}.
     * @return a page of the films with an actor, with their numbers of actors, each a tuple of {@code film}
     *         ({@link Film}) and {@code actors} ({@link Long}).
     */
    @Query(value = """
            SELECT f AS film, COUNT(fa) AS actors
            FROM FilmActor fa
            JOIN fa.film f
            GROUP BY f""",
           countQuery = """
                   SELECT COUNT(DISTINCT fa.film)
                   FROM FilmActor fa""")
    Page<Tuple> selectActorCounts(Pageable pageable);

    /**
     * Selects how many actors a film has, on average, over the films which have at least one
     * ({@code SAKILA_FILM_ACTOR_I_02}, the book's pick, which rounds to two decimal places).
     * <p>
     * The average of the counts of each film is the number of links divided by the number of distinct films among
     * them, so one aggregate query does it, with no subquery, rounded in the query, as the exercise does. The division
     * is a decimal one, by multiplying the dividend by {@code 1.0} first. With no link at all, the division would be
     * of zero by zero, which MySQL answers with {@code NULL} but H2 refuses, so the divisor goes through
     * {@code NULLIF}, and the result is {@code NULL} on both; hence the {@link Optional}. The exercise gives one value,
     * so the method takes no {@link Pageable} and returns no {@link Page}: there is nothing to page or to sort.
     *
     * @return the average number of actors of a film with an actor, rounded to two decimal places; empty if there is
     *         no link.
     */
    @Query("""
            SELECT ROUND(COUNT(fa) * 1.0 / NULLIF(COUNT(DISTINCT fa.film), 0), 2)
            FROM FilmActor fa""")
    Optional<Double> selectAverageActors();

    /**
     * Selects the numbers of actors the films have, with how many films have each ({@code SAKILA_FILM_ACTOR_I_04},
     * native-only, which orders by {@code actors}).
     * <p>
     * A {@link NativeQuery} with the exercise's SQL, which upstream answers in SQL only: the films are counted by
     * their numbers of actors, which a derived table groups first. The SQL is plain, so it runs on H2 as on MySQL. A
     * film with no actor has no link, and is not counted.
     *
     * @return a list of the numbers of actors, with their numbers of films, by the number of actors, each a tuple of
     *         {@code actors} and {@code films} ({@link Long}).
     */
    @NativeQuery("""
            SELECT actors, COUNT(*) AS films
            FROM (SELECT film_id, COUNT(*) AS actors FROM film_actor GROUP BY film_id) t
            GROUP BY actors
            ORDER BY actors""")
    List<Tuple> selectFilmCountsByActors();

    /**
     * Selects the pairs of actors who appear in a film together, with how many films
     * ({@code SAKILA_FILM_ACTOR_A_01}, the book's pick, which takes the first three by {@code films} descending, then
     * by {@code a1.actorId}, then by {@code a2.actorId}).
     * <p>
     * A self-join: every link is paired with every other link of the same film whose actor's id is greater, so each
     * pair is counted once, the first actor's id the smaller; the links are joined with their actors, the pairs are
     * grouped by the two actors themselves, which selects both whole, and counted. The count query counts the pairs of
     * actors, in id order, who share at least one film, through an {@code EXISTS} of the same self-join.
     *
     * @param pageable the page to return, and its sort, by {@code films}, or by an attribute of either actor through
     *                 {@code a1} or {@code a2} ({@code a1.actorId}, {@code a2.lastName}, ...); must not be
     *                 {@code null}.
     * @return a page of the pairs of actors who appear together, with their numbers of films, each a tuple of
     *         {@code actor1} and {@code actor2} ({@link Actor}), the one of the smaller id first, and {@code films}
     *         ({@link Long}).
     */
    @Query(value = """
            SELECT a1 AS actor1, a2 AS actor2, COUNT(fa1) AS films
            FROM FilmActor fa1
            JOIN fa1.actor a1,
                 FilmActor fa2
            JOIN fa2.actor a2
            WHERE fa2.film = fa1.film
              AND a2.actorId > a1.actorId
            GROUP BY a1, a2""",
           countQuery = """
                   SELECT COUNT(a1)
                   FROM Actor a1, Actor a2
                   WHERE a1.actorId < a2.actorId
                     AND EXISTS (SELECT fa1.id.filmId
                                 FROM FilmActor fa1, FilmActor fa2
                                 WHERE fa1.actor = a1
                                   AND fa2.actor = a2
                                   AND fa2.film = fa1.film)""")
    Page<Tuple> selectCoStarCounts(Pageable pageable);

    /**
     * Selects the pairs of films which have exactly the same actors, with how many ({@code SAKILA_FILM_ACTOR_A_03},
     * this module's own extra, which orders by {@code f1.filmId}, then by {@code f2.filmId}).
     * <p>
     * A self-join: every link is paired with every other link of the same actor whose film's id is greater, so each
     * pair of films is counted once, the first film's id the smaller; the links are joined with their films, the
     * pairs are grouped by the two films themselves, which selects both whole, and counted, and a pair is kept when
     * the actors it shares are all the actors of each film, which two correlated subqueries count. The count query
     * counts the same groups, through a derived table of them.
     *
     * @param pageable the page to return, and its sort, by {@code actors}, or by an attribute of either film through
     *                 {@code f1} or {@code f2} ({@code f1.filmId}, {@code f2.title}, ...); must not be
     *                 {@code null}.
     * @return a page of the pairs of films with the same actors, with their numbers of actors, each a tuple of
     *         {@code film1} and {@code film2} ({@link Film}), the one of the smaller id first, and {@code actors}
     *         ({@link Long}).
     */
    @Query(value = """
            SELECT f1 AS film1, f2 AS film2, COUNT(fa1) AS actors
            FROM FilmActor fa1
            JOIN fa1.film f1,
                 FilmActor fa2
            JOIN fa2.film f2
            WHERE fa2.actor = fa1.actor
              AND f2.filmId > f1.filmId
            GROUP BY f1, f2
            HAVING COUNT(fa1) = (SELECT COUNT(x) FROM FilmActor x WHERE x.film = f1)
               AND COUNT(fa1) = (SELECT COUNT(y) FROM FilmActor y WHERE y.film = f2)""",
           countQuery = """
                   SELECT COUNT(*)
                   FROM (SELECT f1.filmId AS filmId1, f2.filmId AS filmId2
                         FROM FilmActor fa1
                         JOIN fa1.film f1,
                              FilmActor fa2
                         JOIN fa2.film f2
                         WHERE fa2.actor = fa1.actor
                           AND f2.filmId > f1.filmId
                         GROUP BY f1, f2
                         HAVING COUNT(fa1) = (SELECT COUNT(x) FROM FilmActor x WHERE x.film = f1)
                            AND COUNT(fa1) = (SELECT COUNT(y) FROM FilmActor y WHERE y.film = f2)) p""")
    Page<Tuple> selectFilmPairsWithSameActors(Pageable pageable);
}
