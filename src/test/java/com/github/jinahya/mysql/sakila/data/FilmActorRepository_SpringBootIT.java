package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Actor;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActorId;
import com.github.jinahya.mysql.sakila.persistence.FilmActorId_;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmActorRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few links of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * counts of the links taken another way. The base's own tests, such as its {@code findById} round trips, run here
 * too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see FilmActorRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilmActorRepository_SpringBootIT
        extends _Repository_SpringBootIT<FilmActorRepository, FilmActor, FilmActorId> {

    private static List<FilmActor> entities;

    /**
     * A sort by the actor's id, then by the film's id, both inside the embedded id; by the attributes' names, since
     * the metamodel's {@code FilmActor_.id} is filled in only once the context is up, after this class is loaded.
     */
    private static final Sort BY_ACTOR_ID_THEN_FILM_ID = Sort.by(
            Sort.Direction.ASC,
            FilmActor_.ID + "." + FilmActorId_.ACTOR_ID,
            FilmActor_.ID + "." + FilmActorId_.FILM_ID
    );

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    FilmActorRepository_SpringBootIT() {
        super(FilmActorRepository.class, FilmActor.class, FilmActorId.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five links, by the actor's id and then the film's, as the fixture for the parameterized
     * tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The links are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_ACTOR_ID_THEN_FILM_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<FilmActor> filmActors() {
        return entities.stream();
    }

    /**
     * Counts the links of the film of the specified id, through a {@link Specification} on the embedded id.
     *
     * @param filmId the id of the film.
     * @return the number of actors of the film.
     */
    private long countByFilmId(final Integer filmId) {
        final Specification<FilmActor> specification =
                (r, q, b) -> b.equal(r.get(FilmActor_.id).get(FilmActorId_.filmId), filmId);
        return repositoryInstance().count(specification);
    }

    /**
     * Counts the films the two actors of the specified ids appear in together, through a {@link Specification} with
     * a correlated subquery: the links of the first actor whose film has a link of the second.
     *
     * @param actorId1 the id of one actor.
     * @param actorId2 the id of the other actor.
     * @return the number of films of both.
     */
    private long countFilmsOfBoth(final Integer actorId1, final Integer actorId2) {
        final Specification<FilmActor> specification = (r, q, b) -> {
            final var other = Objects.requireNonNull(q).subquery(Integer.class);
            final var fa2 = other.from(FilmActor.class);
            other.select(fa2.get(FilmActor_.id).get(FilmActorId_.filmId))
                    .where(b.equal(fa2.get(FilmActor_.film), r.get(FilmActor_.film)),
                           b.equal(fa2.get(FilmActor_.id).get(FilmActorId_.actorId), actorId2));
            return b.and(b.equal(r.get(FilmActor_.id).get(FilmActorId_.actorId), actorId1), b.exists(other));
        };
        return repositoryInstance().count(specification);
    }

    /**
     * Asserts that the specified page is consistent with itself: no more content than its size, and at least as many
     * elements in all as in its content.
     *
     * @param page the page.
     * @param <T>  the type of the elements.
     */
    private static <T> void assertPage(final Page<T> page) {
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(page.getSize());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(page.getNumberOfElements());
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmActorRepository#selectActorCounts(Pageable)} ({@code SAKILA_FILM_ACTOR_I_01}).
     */
    @Nested
    class SelectActorCounts_Test {

        private static final Sort BY_ACTORS_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "actors").and(Sort.by("f.filmId"));

        private static Stream<FilmActor> filmActors() {
            return FilmActorRepository_SpringBootIT.filmActors();
        }

        /**
         * Asserts that a page of the given size, the most cast first as the exercise sorts, is ordered so, that each
         * film is the one of its id, read through {@link FilmRepository}, and that each count is the number of links
         * of the film.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectActorCounts(
                    PageRequest.of(0, size, BY_ACTORS_DESC_THEN_FILM_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("actors", Long.class)).reversed()
                                                 .thenComparing(column("film", Film.class, Film::getFilmId)))
                    .allSatisfy(c -> {
                        final var film = c.get("film", Film.class);
                        final var filmId = film.getFilmId();
                        assertThat(filmRepository.findById(filmId)).hasValue(film);
                        assertThat(c.get("actors", Long.class)).as("actors of film %d", filmId)
                                .isPositive()
                                .isEqualTo(countByFilmId(filmId));
                    });
        }

        /**
         * Asserts that the first five, the most cast first and then by id as the exercise sorts, are the exercise's
         * output.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectActorCounts(PageRequest.of(0, 5, BY_ACTORS_DESC_THEN_FILM_ID));
            assertThat(page.getContent())
                    .extracting(column("film", Film.class, Film::getFilmId),
                                column("film", Film.class, Film::getTitle),
                                column("actors", Long.class))
                    .containsExactly(
                            tuple(508, "LAMBS CINCINATTI", 15L),
                            tuple(87, "BOONDOCK BALLROOM", 13L),
                            tuple(146, "CHITTY LOCK", 13L),
                            tuple(188, "CRAZY HOME", 13L),
                            tuple(249, "DRACULA CRYSTAL", 13L)
                    );
        }

        /**
         * Asserts that the film of a selected link is among the films selected, with at least one actor.
         *
         * @param filmActor one of the links selected in {@link FilmActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("filmActors")
        @ParameterizedTest
        void __ContainsFilmOfFixture(final FilmActor filmActor) {
            final var all = repositoryInstance().selectActorCounts(Pageable.unpaged(BY_ACTORS_DESC_THEN_FILM_ID));
            assertThat(all.getContent())
                    .anySatisfy(c -> {
                        assertThat(c.get("film", Film.class).getFilmId()).isEqualTo(filmActor.getId().getFilmId());
                        assertThat(c.get("actors", Long.class)).isPositive();
                    });
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectAverageActors()} ({@code SAKILA_FILM_ACTOR_I_02}).
     */
    @Nested
    class SelectAverageActors_Test {

        /**
         * Asserts that the average is the exercise's output, {@code 5.48}.
         */
        @Test
        void __Expected() {
            assertThat(repositoryInstance().selectAverageActors()).hasValue(5.48d);
        }

        /**
         * Asserts that the average is present, and is the number of all links over the number of films with a link,
         * which {@link FilmActorRepository#selectActorCounts(Pageable)} groups, rounded to two decimal places.
         */
        @Test
        void __() {
            final var average = repositoryInstance().selectAverageActors();
            final var links = repositoryInstance().count();
            final var films = repositoryInstance().selectActorCounts(Pageable.unpaged()).getTotalElements();
            assumeThat(films).isPositive();
            final var expected = Math.round((double) links / films * 100.0d) / 100.0d;
            assertThat(average).hasValueSatisfying(v -> {
                assertThat(v).isCloseTo(expected, within(1e-9d));
            });
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectFilmCountsByActors()} ({@code SAKILA_FILM_ACTOR_I_04}).
     */
    @Nested
    class SelectFilmCountsByActors_Test {

        /**
         * Asserts that the list is the exercise's output, by the number of actors.
         */
        @Test
        void __Expected() {
            final var list = repositoryInstance().selectFilmCountsByActors();
            assertThat(list)
                    .extracting(longColumn("actors"), longColumn("films"))
                    .containsExactly(
                            tuple(1L, 21L),
                            tuple(2L, 69L),
                            tuple(3L, 119L),
                            tuple(4L, 137L),
                            tuple(5L, 195L),
                            tuple(6L, 150L),
                            tuple(7L, 119L),
                            tuple(8L, 90L),
                            tuple(9L, 49L),
                            tuple(10L, 21L),
                            tuple(11L, 14L),
                            tuple(12L, 6L),
                            tuple(13L, 6L),
                            tuple(15L, 1L)
                    );
        }

        /**
         * Asserts that the films counted are, in all, the films with a link, and their actors, in all, the links,
         * both through {@link FilmActorRepository#selectActorCounts(Pageable)}'s count and the repository's own.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectFilmCountsByActors();
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparing(longColumn("actors")));
            final var films = list.stream().mapToLong(c -> c.get("films", Number.class).longValue()).sum();
            final var links = list.stream()
                    .mapToLong(c -> c.get("actors", Number.class).longValue()
                                    * c.get("films", Number.class).longValue())
                    .sum();
            assertThat(films).isEqualTo(repositoryInstance().selectActorCounts(Pageable.unpaged()).getTotalElements());
            assertThat(links).isEqualTo(repositoryInstance().count());
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectCoStarCounts(Pageable)} ({@code SAKILA_FILM_ACTOR_A_01}).
     */
    @Nested
    class SelectCoStarCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_ACTOR_IDS =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("a1.actorId", "a2.actorId"));

        /**
         * Asserts that the first three, the pairs appearing together the most first and then by their ids as the
         * exercise sorts, are the exercise's output.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectCoStarCounts(
                    PageRequest.of(0, 3, BY_FILMS_DESC_THEN_ACTOR_IDS));
            assertThat(page.getContent())
                    .extracting(column("actor1", Actor.class, Actor::getActorId),
                                column("actor2", Actor.class, Actor::getActorId),
                                column("films", Long.class))
                    .containsExactly(
                            tuple(27, 60, 7L),
                            tuple(15, 198, 6L),
                            tuple(21, 108, 6L)
                    );
        }

        /**
         * Asserts that a page of the given size, the pairs appearing together the most first as the exercise sorts,
         * is ordered so, that each pair has the smaller id first, and that each count is the number of films of
         * both actors, counted through a correlated subquery.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 3, 10})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectCoStarCounts(
                    PageRequest.of(0, size, BY_FILMS_DESC_THEN_ACTOR_IDS));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("films", Long.class)).reversed()
                                                 .thenComparing(column("actor1", Actor.class, Actor::getActorId))
                                                 .thenComparing(column("actor2", Actor.class, Actor::getActorId)))
                    .allSatisfy(c -> {
                        final var actorId1 = c.get("actor1", Actor.class).getActorId();
                        final var actorId2 = c.get("actor2", Actor.class).getActorId();
                        assertThat(actorId1).isLessThan(actorId2);
                        assertThat(c.get("films", Long.class)).as("films of actors %d and %d", actorId1, actorId2)
                                .isPositive()
                                .isEqualTo(countFilmsOfBoth(actorId1, actorId2));
                    });
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectFilmPairsWithSameActors(Pageable)} ({@code SAKILA_FILM_ACTOR_A_03}).
     */
    @Nested
    class SelectFilmPairsWithSameActors_Test {

        private static final Sort BY_FILM_IDS = Sort.by("f1.filmId", "f2.filmId");

        /**
         * Asserts that the pairs, by the films' ids as the exercise sorts, are the exercise's output, one pair in all.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectFilmPairsWithSameActors(PageRequest.of(0, 5, BY_FILM_IDS));
            assertThat(page.getContent())
                    .extracting(column("film1", Film.class, Film::getFilmId),
                                column("film2", Film.class, Film::getFilmId),
                                column("actors", Long.class))
                    .containsExactly(tuple(97, 556, 1L));
            assertThat(page.getTotalElements()).isEqualTo(1L);
        }

        /**
         * Asserts that each pair selected has the smaller id first, and that each count is the number of actors of
         * either film.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectFilmPairsWithSameActors(Pageable.unpaged(BY_FILM_IDS));
            assertThat(page.getContent())
                    .allSatisfy(c -> {
                        final var filmId1 = c.get("film1", Film.class).getFilmId();
                        final var filmId2 = c.get("film2", Film.class).getFilmId();
                        assertThat(filmId1).isLessThan(filmId2);
                        assertThat(c.get("actors", Long.class))
                                .isEqualTo(countByFilmId(filmId1))
                                .isEqualTo(countByFilmId(filmId2));
                    });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
