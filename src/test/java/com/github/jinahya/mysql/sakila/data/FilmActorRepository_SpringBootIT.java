package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmActorRepository.ActorPairCount;
import com.github.jinahya.mysql.sakila.data.FilmActorRepository.FilmActorCount;
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
     * Tests {@link FilmActorRepository#selectActorCounts(Pageable)} ({@code SAKILA-FILM_ACTOR-I-01}).
     */
    @Nested
    class SelectActorCounts_Test {

        private static final Sort BY_ACTORS_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "actors").and(Sort.by("filmId"));

        private static Stream<FilmActor> filmActors() {
            return FilmActorRepository_SpringBootIT.filmActors();
        }

        /**
         * Asserts that a page of the given size, the most cast first as the exercise sorts, is ordered so, that each
         * title is the film's, read through {@link FilmRepository}, and that each count is the number of links of
         * the film.
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
                    .isSortedAccordingTo(Comparator.comparingLong(FilmActorCount::getActors).reversed()
                                                 .thenComparing(FilmActorCount::getFilmId))
                    .allSatisfy(c -> {
                        final var film = filmRepository.findById(c.getFilmId()).orElseThrow();
                        assertThat(film.getTitle()).isEqualTo(c.getTitle());
                        assertThat(c.getActors()).as("actors of film %d", c.getFilmId())
                                .isPositive()
                                .isEqualTo(countByFilmId(c.getFilmId()));
                    });
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
                        assertThat(c.getFilmId()).isEqualTo(filmActor.getId().getFilmId());
                        assertThat(c.getActors()).isPositive();
                    });
        }
    }

    /**
     * Tests {@link FilmActorRepository#selectAverageActors()} ({@code SAKILA-FILM_ACTOR-I-02}).
     */
    @Nested
    class SelectAverageActors_Test {

        /**
         * Asserts that the average is present, and is the number of all links over the number of films with a link,
         * which {@link FilmActorRepository#selectActorCounts(Pageable)} groups; the exercise rounds it to two decimal
         * places, which is done here, not in the query.
         */
        @Test
        void __() {
            final var average = repositoryInstance().selectAverageActors();
            final var links = repositoryInstance().count();
            final var films = repositoryInstance().selectActorCounts(Pageable.unpaged()).getTotalElements();
            assumeThat(films).isPositive();
            final var expected = (double) links / films;
            assertThat(average).hasValueSatisfying(v -> {
                assertThat(v).isCloseTo(expected, within(1e-3d));
                assertThat(Math.round(v * 100.0d) / 100.0d).isEqualTo(Math.round(expected * 100.0d) / 100.0d);
            });
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
                    .isSortedAccordingTo(Comparator.comparingLong(ActorPairCount::getFilms).reversed()
                                                 .thenComparing(ActorPairCount::getActorId1)
                                                 .thenComparing(ActorPairCount::getActorId2))
                    .allSatisfy(c -> {
                        assertThat(c.getActorId1()).isLessThan(c.getActorId2());
                        assertThat(c.getFilms()).as("films of actors %d and %d", c.getActorId1(), c.getActorId2())
                                .isPositive()
                                .isEqualTo(countFilmsOfBoth(c.getActorId1(), c.getActorId2()));
                    });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
