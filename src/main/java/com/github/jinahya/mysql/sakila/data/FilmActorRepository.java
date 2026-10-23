package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActorId;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
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
 * ({@code SAKILA-FILM_ACTOR-I-01}, ...) and by its role: the book's pick, this module's own extra, or native-only.
 * The exercises are context, not contract: a method that shows rows takes a {@link Pageable}, the page and the sort,
 * and returns a {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are the caller's
 * {@code PageRequest}; each method's javadoc says which sort that is. Every exercise of this table groups and counts
 * the links, which a {@link Specification}, which only filters, cannot do; so each method, {@code select...},
 * declares its JPQL with {@link Query}, with a count query of its own since Spring Data cannot derive one from a
 * grouped select, and returns a projection, one of the interfaces declared here, which Spring Data backs with the
 * selected columns, each aliased with the name of the getter it feeds. Those aliases are what the {@code Pageable}
 * sorts by; an interface rather than a record, because Spring Data rewrites a record projection into a constructor
 * expression, which drops the aliases, and the sort with them. The exercise that gives a single value, an average,
 * returns that value, not a page. An exercise upstream flags native-only is a {@link NativeQuery} with the exercise's
 * SQL, returning a {@link List} in the SQL's own order.
 *
 * @see FilmActor
 * @see FilmActor_
 */
@Repository
public interface FilmActorRepository
        extends _Repository<FilmActor, FilmActorId> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * A film, by id and title, and how many actors it has: a projection of {@link #selectActorCounts(Pageable)},
     * sortable by {@code filmId}, {@code title} and {@code actors}.
     */
    interface FilmActorCount {

        /**
         * Returns the id of the film.
         *
         * @return the id of the film.
         */
        Integer getFilmId();

        /**
         * Returns the title of the film.
         *
         * @return the title of the film.
         */
        String getTitle();

        /**
         * Returns the number of actors of the film.
         *
         * @return the number of actors.
         */
        long getActors();
    }

    /**
     * A pair of actors, by their ids, and how many films they appear in together: a projection of
     * {@link #selectCoStarCounts(Pageable)}, sortable by {@code actorId1}, {@code actorId2} and {@code films}.
     */
    interface ActorPairCount {

        /**
         * Returns the id of the first actor of the pair, the smaller of the two.
         *
         * @return the id of the first actor.
         */
        Integer getActorId1();

        /**
         * Returns the id of the second actor of the pair, the greater of the two.
         *
         * @return the id of the second actor.
         */
        Integer getActorId2();

        /**
         * Returns the number of films the two actors appear in together.
         *
         * @return the number of films.
         */
        long getFilms();
    }

    /**
     * A number of actors, and how many films have that many: a projection of {@link #selectFilmCountsByActors()}.
     */
    interface ActorsFilmCount {

        /**
         * Returns the number of actors.
         *
         * @return the number of actors.
         */
        long getActors();

        /**
         * Returns the number of films which have {@link #getActors()} actors.
         *
         * @return the number of films.
         */
        long getFilms();
    }

    /**
     * A pair of films, by their ids, which have the same actors, and how many: a projection of
     * {@link #selectFilmPairsWithSameActors(Pageable)}, sortable by {@code filmId1}, {@code filmId2} and
     * {@code actors}.
     */
    interface FilmPairActorCount {

        /**
         * Returns the id of the first film of the pair, the smaller of the two.
         *
         * @return the id of the first film.
         */
        Integer getFilmId1();

        /**
         * Returns the id of the second film of the pair, the greater of the two.
         *
         * @return the id of the second film.
         */
        Integer getFilmId2();

        /**
         * Returns the number of actors the two films share, which is the number of actors of each.
         *
         * @return the number of actors.
         */
        long getActors();
    }

    /**
     * Selects the films which have at least one actor, with how many ({@code SAKILA-FILM_ACTOR-I-01}, this module's
     * own extra, which takes the first five by {@code actors} descending, then by {@code filmId}, so that the five are
     * always the same five).
     * <p>
     * The links are grouped by their film, joined for its id and title, and counted; a film with no link is not in
     * the page. The count query counts the distinct films of the links.
     *
     * @param pageable the page to return, and its sort, by {@code filmId}, {@code title} or {@code actors}; must not
     *                 be {@code null}.
     * @return a page of the films with an actor, with their numbers of actors.
     */
    @Query(value = """
            SELECT f.filmId AS filmId, f.title AS title, COUNT(fa) AS actors
            FROM FilmActor fa
            JOIN fa.film f
            GROUP BY f.filmId, f.title""",
           countQuery = """
                   SELECT COUNT(DISTINCT fa.film)
                   FROM FilmActor fa""")
    Page<FilmActorCount> selectActorCounts(Pageable pageable);

    /**
     * Selects how many actors a film has, on average, over the films which have at least one
     * ({@code SAKILA-FILM_ACTOR-I-02}, the book's pick, which rounds to two decimal places).
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
     * Selects the numbers of actors the films have, with how many films have each ({@code SAKILA-FILM_ACTOR-I-04},
     * native-only, which orders by {@code actors}).
     * <p>
     * A {@link NativeQuery} with the exercise's SQL, which upstream answers in SQL only: the films are counted by
     * their numbers of actors, which a derived table groups first. The SQL is plain, so it runs on H2 as on MySQL. A
     * film with no actor has no link, and is not counted.
     *
     * @return a list of the numbers of actors, with their numbers of films, by the number of actors.
     */
    @NativeQuery("""
            SELECT actors, COUNT(*) AS films
            FROM (SELECT film_id, COUNT(*) AS actors FROM film_actor GROUP BY film_id) t
            GROUP BY actors
            ORDER BY actors""")
    List<ActorsFilmCount> selectFilmCountsByActors();

    /**
     * Selects the pairs of actors who appear in a film together, with how many films
     * ({@code SAKILA-FILM_ACTOR-A-01}, the book's pick, which takes the first three by {@code films} descending, then
     * by {@code actorId1}, then by {@code actorId2}).
     * <p>
     * A self-join: every link is paired with every other link of the same film whose actor's id is greater, so each
     * pair is counted once, the first actor's id the smaller; the pairs are grouped by the two ids and counted. The
     * count query counts the pairs of actors, in id order, who share at least one film, through an {@code EXISTS} of
     * the same self-join.
     *
     * @param pageable the page to return, and its sort, by {@code actorId1}, {@code actorId2} or {@code films}; must
     *                 not be {@code null}.
     * @return a page of the pairs of actors who appear together, with their numbers of films.
     */
    @Query(value = """
            SELECT fa1.id.actorId AS actorId1, fa2.id.actorId AS actorId2, COUNT(fa1) AS films
            FROM FilmActor fa1, FilmActor fa2
            WHERE fa2.film = fa1.film
              AND fa2.id.actorId > fa1.id.actorId
            GROUP BY fa1.id.actorId, fa2.id.actorId""",
           countQuery = """
                   SELECT COUNT(a1)
                   FROM Actor a1, Actor a2
                   WHERE a1.actorId < a2.actorId
                     AND EXISTS (SELECT fa1.id.filmId
                                 FROM FilmActor fa1, FilmActor fa2
                                 WHERE fa1.actor = a1
                                   AND fa2.actor = a2
                                   AND fa2.film = fa1.film)""")
    Page<ActorPairCount> selectCoStarCounts(Pageable pageable);

    /**
     * Selects the pairs of films which have exactly the same actors, with how many ({@code SAKILA-FILM_ACTOR-A-03},
     * this module's own extra, which orders by {@code filmId1}, then by {@code filmId2}).
     * <p>
     * A self-join: every link is paired with every other link of the same actor whose film's id is greater, so each
     * pair of films is counted once, the first film's id the smaller; the pairs are grouped by the two ids and counted,
     * and a pair is kept when the actors it shares are all the actors of each film, which two correlated subqueries
     * count. The count query counts the same groups, through a derived table of them.
     *
     * @param pageable the page to return, and its sort, by {@code filmId1}, {@code filmId2} or {@code actors}; must
     *                 not be {@code null}.
     * @return a page of the pairs of films with the same actors, with their numbers of actors.
     */
    @Query(value = """
            SELECT fa1.id.filmId AS filmId1, fa2.id.filmId AS filmId2, COUNT(fa1) AS actors
            FROM FilmActor fa1, FilmActor fa2
            WHERE fa2.actor = fa1.actor
              AND fa2.id.filmId > fa1.id.filmId
            GROUP BY fa1.id.filmId, fa2.id.filmId
            HAVING COUNT(fa1) = (SELECT COUNT(x) FROM FilmActor x WHERE x.id.filmId = fa1.id.filmId)
               AND COUNT(fa1) = (SELECT COUNT(y) FROM FilmActor y WHERE y.id.filmId = fa2.id.filmId)""",
           countQuery = """
                   SELECT COUNT(*)
                   FROM (SELECT fa1.id.filmId AS filmId1, fa2.id.filmId AS filmId2
                         FROM FilmActor fa1, FilmActor fa2
                         WHERE fa2.actor = fa1.actor
                           AND fa2.id.filmId > fa1.id.filmId
                         GROUP BY fa1.id.filmId, fa2.id.filmId
                         HAVING COUNT(fa1) = (SELECT COUNT(x) FROM FilmActor x WHERE x.id.filmId = fa1.id.filmId)
                            AND COUNT(fa1) = (SELECT COUNT(y) FROM FilmActor y WHERE y.id.filmId = fa2.id.filmId)) p""")
    Page<FilmPairActorCount> selectFilmPairsWithSameActors(Pageable pageable);
}
