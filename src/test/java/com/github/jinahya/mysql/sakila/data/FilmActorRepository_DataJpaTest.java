package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmActorRepository.ActorPairCount;
import com.github.jinahya.mysql.sakila.data.FilmActorRepository.ActorsFilmCount;
import com.github.jinahya.mysql.sakila.data.FilmActorRepository.FilmActorCount;
import com.github.jinahya.mysql.sakila.data.FilmActorRepository.FilmPairActorCount;
import com.github.jinahya.mysql.sakila.persistence.Actor;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActorId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests {@link FilmActorRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the films, actors and links it needs, through
 * the fixtures, and the slice rolls them back when it ends, so a test starts over empty tables and can count what it
 * finds. The ids of the actors and of the films ascend in the order they are saved, which is what the pair
 * exercises order by.
 *
 * @see FilmActorRepository_SpringBootIT
 */
class FilmActorRepository_DataJpaTest
        extends _Repository_DataJpaTest<FilmActorRepository, FilmActor, FilmActorId> {

    /**
     * Creates a new instance.
     */
    FilmActorRepository_DataJpaTest() {
        super(FilmActorRepository.class, FilmActor.class, FilmActorId.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Saves the specified actors as the actors of the specified film.
     *
     * @param film   the film.
     * @param actors the actors.
     */
    private void cast(final Film film, final Actor... actors) {
        for (final var actor : actors) {
            fixtures().filmActor(actor, film);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link FilmActorRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Saves a link of an actor and a film, deletes it, and asserts that finding it by its id returns an empty
         * optional.
         * <p>
         * An id that is known to be unknown has to come from a link that was saved: a {@link FilmActorId} cannot be
         * built outside its package, and a link has no id until it is saved. The delete is flushed before the find
         * goes to the database.
         */
        @Test
        void _Empty_Deleted() {
            final var saved = fixtures().filmActor(fixtures().actor(), fixtures().film());
            final var id = saved.getId();
            repositoryInstance().delete(saved);
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves a link of an actor and a film, and asserts that finding it by its id returns an equal link, of that
         * actor and that film.
         * <p>
         * The id of a link is derived from the two, so {@code save} merges rather than persists, and what comes back
         * may be a copy of what was passed; hence equal, not the same.
         */
        @Test
        void _NotEmpty_Known() {
            final var actor = fixtures().actor();
            final var film = fixtures().film();
            final var saved = fixtures().filmActor(actor, film);
            final var found = repositoryInstance().findById(saved.getId());
            assertThat(found).hasValueSatisfying(v -> {
                assertThat(v).isEqualTo(saved);
                assertThat(v.getActor()).isEqualTo(actor);
                assertThat(v.getFilm()).isEqualTo(film);
            });
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmActorRepository#selectActorCounts(Pageable)} ({@code SAKILA-FILM_ACTOR-I-01}).
     */
    @Nested
    class SelectActorCounts_Test {

        private static final Sort BY_ACTORS_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "actors").and(Sort.by("filmId"));

        /**
         * Saves a film with no actor, and asserts that the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoLink() {
            fixtures().film();
            final var page = repositoryInstance().selectActorCounts(
                    PageRequest.of(0, 5, BY_ACTORS_DESC_THEN_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves films of three, one and no actors, and asserts that a page of five, the most cast first as the
         * exercise sorts, holds the first two with their ids, titles and counts, two in all, and that a page of one
         * holds the most cast alone, still two in all.
         */
        @Test
        void __() {
            final var three = fixtures().film();
            final var one = fixtures().film();
            fixtures().film();
            cast(three, fixtures().actor(), fixtures().actor(), fixtures().actor());
            cast(one, fixtures().actor());
            final var page = repositoryInstance().selectActorCounts(
                    PageRequest.of(0, 5, BY_ACTORS_DESC_THEN_FILM_ID));
            assertThat(page.getContent())
                    .extracting(FilmActorCount::getFilmId, FilmActorCount::getTitle, FilmActorCount::getActors)
                    .containsExactly(
                            tuple(three.getFilmId(), three.getTitle(), 3L),
                            tuple(one.getFilmId(), one.getTitle(), 1L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(2L);
            final var first = repositoryInstance().selectActorCounts(
                    PageRequest.of(0, 1, BY_ACTORS_DESC_THEN_FILM_ID));
            assertThat(first.getContent())
                    .extracting(FilmActorCount::getFilmId)
                    .containsExactly(three.getFilmId());
            assertThat(first.getTotalElements()).isEqualTo(2L);
            assertThat(first.getTotalPages()).isEqualTo(2);
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectActorCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectAverageActors()} ({@code SAKILA-FILM_ACTOR-I-02}).
     */
    @Nested
    class SelectAverageActors_Test {

        /**
         * Asserts that, with no link, the average is empty.
         * <p>
         * Zero links over zero films would be a division of zero by zero, which the query turns into a division by
         * {@code NULL}, whose result is {@code NULL}.
         */
        @Test
        void _Empty_NoLink() {
            assertThat(repositoryInstance().selectAverageActors()).isEmpty();
        }

        /**
         * Saves films of three actors and of one, and a film with none, and asserts that the average is two: four
         * links over the two films that have one, the film with none not counted.
         */
        @Test
        void __() {
            final var three = fixtures().film();
            final var one = fixtures().film();
            fixtures().film();
            cast(three, fixtures().actor(), fixtures().actor(), fixtures().actor());
            cast(one, fixtures().actor());
            final var average = repositoryInstance().selectAverageActors();
            assertThat(average).hasValueSatisfying(v -> {
                assertThat(v).isCloseTo(2.0d, within(1e-9d));
            });
        }

        /**
         * Saves films of two actors, of one and of one, and asserts that the average, four links over three films,
         * {@code 1.333...}, is rounded to two decimal places, {@code 1.33}, as the exercise does.
         */
        @Test
        void __Rounded() {
            cast(fixtures().film(), fixtures().actor(), fixtures().actor());
            cast(fixtures().film(), fixtures().actor());
            cast(fixtures().film(), fixtures().actor());
            final var average = repositoryInstance().selectAverageActors();
            assertThat(average).hasValueSatisfying(v -> {
                assertThat(v).isCloseTo(1.33d, within(1e-9d));
            });
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectFilmCountsByActors()} ({@code SAKILA-FILM_ACTOR-I-04}).
     * <p>
     * The SQL of the native query is plain, a {@code GROUP BY} over a derived table, so it runs on the H2 too.
     */
    @Nested
    class SelectFilmCountsByActors_Test {

        /**
         * Saves a film with no actor, and asserts that the list is empty: a film with no link is not counted.
         */
        @Test
        void _Empty_NoLink() {
            fixtures().film();
            assertThat(repositoryInstance().selectFilmCountsByActors()).isEmpty();
        }

        /**
         * Saves two films of two actors, one of one actor and one of none, and asserts that the list holds one film
         * of one actor and two of two, by the number of actors as the exercise orders.
         */
        @Test
        void __() {
            cast(fixtures().film(), fixtures().actor(), fixtures().actor());
            cast(fixtures().film(), fixtures().actor());
            cast(fixtures().film(), fixtures().actor(), fixtures().actor());
            fixtures().film();
            final var list = repositoryInstance().selectFilmCountsByActors();
            assertThat(list)
                    .extracting(ActorsFilmCount::getActors, ActorsFilmCount::getFilms)
                    .containsExactly(tuple(1L, 1L), tuple(2L, 2L));
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectCoStarCounts(Pageable)} ({@code SAKILA-FILM_ACTOR-A-01}).
     */
    @Nested
    class SelectCoStarCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_ACTOR_IDS =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("actorId1", "actorId2"));

        /**
         * Saves a film with one actor, and asserts that the page is empty, with no elements in all: one actor makes
         * no pair.
         */
        @Test
        void _Empty_NoPair() {
            cast(fixtures().film(), fixtures().actor());
            final var page = repositoryInstance().selectCoStarCounts(
                    PageRequest.of(0, 3, BY_FILMS_DESC_THEN_ACTOR_IDS));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves three actors, a film of all three and a film of the first two, and asserts that the page holds the
         * three pairs, the first two actors together in two films first, then the other two pairs by their ids as
         * the exercise sorts, each pair once with the smaller id first, three in all; and that a page of one holds
         * the first pair alone, still three in all.
         * <p>
         * The actors are saved in order, so their ids ascend in that order.
         */
        @Test
        void __() {
            final var a1 = fixtures().actor();
            final var a2 = fixtures().actor();
            final var a3 = fixtures().actor();
            assertThat(List.of(a1.getActorId(), a2.getActorId(), a3.getActorId())).isSorted();
            cast(fixtures().film(), a1, a2, a3);
            cast(fixtures().film(), a1, a2);
            final var page = repositoryInstance().selectCoStarCounts(
                    PageRequest.of(0, 5, BY_FILMS_DESC_THEN_ACTOR_IDS));
            assertThat(page.getContent())
                    .extracting(ActorPairCount::getActorId1, ActorPairCount::getActorId2, ActorPairCount::getFilms)
                    .containsExactly(
                            tuple(a1.getActorId(), a2.getActorId(), 2L),
                            tuple(a1.getActorId(), a3.getActorId(), 1L),
                            tuple(a2.getActorId(), a3.getActorId(), 1L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(3L);
            final var first = repositoryInstance().selectCoStarCounts(
                    PageRequest.of(0, 1, BY_FILMS_DESC_THEN_ACTOR_IDS));
            assertThat(first.getContent())
                    .extracting(ActorPairCount::getActorId1, ActorPairCount::getActorId2, ActorPairCount::getFilms)
                    .containsExactly(tuple(a1.getActorId(), a2.getActorId(), 2L));
            assertThat(first.getTotalElements()).isEqualTo(3L);
            assertThat(first.getTotalPages()).isEqualTo(3);
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCoStarCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectFilmPairsWithSameActors(Pageable)} ({@code SAKILA-FILM_ACTOR-A-03}).
     */
    @Nested
    class SelectFilmPairsWithSameActors_Test {

        private static final Sort BY_FILM_IDS = Sort.by("filmId1", "filmId2");

        /**
         * Saves a film of two actors and a film of one of them, and asserts that the page is empty, with no elements
         * in all: the two films share an actor, but not all of them.
         */
        @Test
        void _Empty_NoSameActors() {
            final var a = fixtures().actor();
            final var b = fixtures().actor();
            cast(fixtures().film(), a, b);
            cast(fixtures().film(), a);
            final var page = repositoryInstance().selectFilmPairsWithSameActors(PageRequest.of(0, 5, BY_FILM_IDS));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves two films of the actors {@code a} and {@code b}, two films of {@code a} alone, a film of {@code a},
         * {@code b} and {@code c}, and a film of {@code c} alone, and asserts that the page holds the two pairs of
         * films with the same actors, with their numbers of actors, by the films' ids as the exercise orders, each
         * pair once with the smaller id first, two in all; and that a page of one holds the first pair alone, still
         * two in all.
         * <p>
         * The films are saved in order, so their ids ascend in that order.
         */
        @Test
        void __() {
            final var a = fixtures().actor();
            final var b = fixtures().actor();
            final var c = fixtures().actor();
            final var ab1 = fixtures().film();
            final var a1 = fixtures().film();
            final var ab2 = fixtures().film();
            final var abc = fixtures().film();
            final var a2 = fixtures().film();
            final var c1 = fixtures().film();
            cast(ab1, a, b);
            cast(a1, a);
            cast(ab2, a, b);
            cast(abc, a, b, c);
            cast(a2, a);
            cast(c1, c);
            final var page = repositoryInstance().selectFilmPairsWithSameActors(PageRequest.of(0, 5, BY_FILM_IDS));
            assertThat(page.getContent())
                    .extracting(FilmPairActorCount::getFilmId1, FilmPairActorCount::getFilmId2,
                                FilmPairActorCount::getActors)
                    .containsExactly(
                            tuple(ab1.getFilmId(), ab2.getFilmId(), 2L),
                            tuple(a1.getFilmId(), a2.getFilmId(), 1L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(2L);
            final var first = repositoryInstance().selectFilmPairsWithSameActors(PageRequest.of(0, 1, BY_FILM_IDS));
            assertThat(first.getContent())
                    .extracting(FilmPairActorCount::getFilmId1, FilmPairActorCount::getFilmId2)
                    .containsExactly(tuple(ab1.getFilmId(), ab2.getFilmId()));
            assertThat(first.getTotalElements()).isEqualTo(2L);
            assertThat(first.getTotalPages()).isEqualTo(2);
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectFilmPairsWithSameActors(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
