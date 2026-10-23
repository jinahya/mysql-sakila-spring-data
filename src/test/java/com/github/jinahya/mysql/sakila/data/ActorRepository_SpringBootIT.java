package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.ActorRepository.ActorFilmCount;
import com.github.jinahya.mysql.sakila.data.ActorRepository.ActorLongestFilm;
import com.github.jinahya.mysql.sakila.data.ActorRepository.LastNameCount;
import com.github.jinahya.mysql.sakila.persistence.Actor;
import com.github.jinahya.mysql.sakila.persistence.Actor_;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link ActorRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few actors of the
 * sample data, selected once in {@link #__()}, and each test looks every one of them up again by one of its names, or
 * checks a query of the exercises against them. The base's own tests, such as its {@code findById} round trips, run
 * here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see ActorRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActorRepository_SpringBootIT
        extends _Repository_SpringBootIT<ActorRepository, Actor, Integer> {

    private static List<Actor> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Actor_.actorId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_ACTOR_ID = Sort.by(Sort.Direction.ASC, Actor_.ACTOR_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    ActorRepository_SpringBootIT() {
        super(ActorRepository.class, Actor.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five actors, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The actors are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_ACTOR_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Actor> actors() {
        return entities.stream();
    }

    private static Stream<String> lastNames() {
        return entities.stream().map(Actor::getLastName).distinct();
    }

    private static Stream<String> firstNamePrefixes() {
        return entities.stream().map(a -> a.getFirstName().substring(0, 2)).distinct();
    }

    /**
     * Lists the {@code film_actor} links of the specified actor, through {@link FilmActorRepository}.
     * <p>
     * The actor's own {@code filmActors} is not public upstream, so the links are read by a {@link Specification}.
     *
     * @param actor the actor.
     * @return the links of {@code actor}.
     */
    private List<FilmActor> filmActorsOf(final Actor actor) {
        final Specification<FilmActor> specification = (r, q, b) -> b.equal(r.get(FilmActor_.actor), actor);
        return filmActorRepository.findAll(specification);
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

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link ActorRepository#findAllByFirstName(String, Pageable)}.
     */
    @Nested
    class FindAllByFirstName_Test {

        private static Stream<Actor> actors() {
            return ActorRepository_SpringBootIT.actors();
        }

        /**
         * Asserts that the first name of a selected actor finds it, among actors of that name only.
         * <p>
         * With no transaction around the test, the find runs in a persistence context of its own, so the actor found
         * is a second instance of the same row, equal to the selected one by id. The names are compared ignoring case,
         * as the database compares them.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        void __(final Actor actor) {
            final var firstName = actor.getFirstName();
            final var found = repositoryInstance().findAllByFirstName(firstName, Pageable.unpaged());
            assertThat(found)
                    .isNotEmpty()
                    .contains(actor)
                    .allSatisfy(a -> {
                        assertThat(a).isNotSameAs(actor);
                        assertThat(a.getFirstName()).isEqualToIgnoringCase(firstName);
                    });
        }

        /**
         * Runs the lookup in one transaction, and asserts that the actor found is the instance managed in it.
         * <p>
         * The selected actor is detached, so the test first loads the row again, into the transaction's persistence
         * context; the row the query then reads resolves to that managed instance. The transaction is rolled back when
         * the test ends.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        @Transactional
        void __Transactional(final Actor actor) {
            final var managed = repositoryInstance().findById(actor.getActorId()).orElseThrow();
            final var found = repositoryInstance().findAllByFirstName(managed.getFirstName(), Pageable.unpaged());
            assertThat(found).anySatisfy(a -> {
                assertThat(a).isSameAs(managed);
            });
        }

        /**
         * Asserts that the first page of one, sorted by id, holds the first of all actors of the selected actor's first
         * name.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        void __Paged(final Actor actor) {
            final var firstName = actor.getFirstName();
            final var all = repositoryInstance().findAllByFirstName(
                    firstName, PageRequest.of(0, Integer.MAX_VALUE, BY_ACTOR_ID));
            assertThat(all).isNotEmpty();
            final var page = repositoryInstance().findAllByFirstName(firstName, PageRequest.of(0, 1, BY_ACTOR_ID));
            assertThat(page).containsExactly(all.getFirst());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link ActorRepository#findAllByLastName(String, Pageable)}.
     */
    @Nested
    class FindAllByLastName_Test {

        private static Stream<Actor> actors() {
            return ActorRepository_SpringBootIT.actors();
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final Actor actor) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByLastName(actor.getLastName(), Pageable.unpaged())) {
                    found.forEach(a -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts, in one transaction, that the last name of a selected actor streams it, as the instance managed in
         * the transaction, among actors of that name only.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        @Transactional
        void __Transactional(final Actor actor) {
            final var managed = repositoryInstance().findById(actor.getActorId()).orElseThrow();
            final var lastName = managed.getLastName();
            try (var found = repositoryInstance().findAllByLastName(lastName, Pageable.unpaged())) {
                final var list = found.toList();
                assertThat(list)
                        .isNotEmpty()
                        .contains(managed)
                        .allSatisfy(a -> {
                            assertThat(a.getLastName()).isEqualToIgnoringCase(lastName);
                        });
                assertThat(list).anySatisfy(a -> assertThat(a).isSameAs(managed));
            }
        }

        /**
         * Asserts, in one transaction, that a page of one, sorted by id, streams the first of all actors of the
         * selected actor's last name.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        @Transactional
        void __PagedTransactional(final Actor actor) {
            final var lastName = actor.getLastName();
            final Actor first;
            try (var all = repositoryInstance().findAllByLastName(
                    lastName, PageRequest.of(0, Integer.MAX_VALUE, BY_ACTOR_ID))) {
                first = all.findFirst().orElseThrow();
            }
            try (var page = repositoryInstance().findAllByLastName(lastName, PageRequest.of(0, 1, BY_ACTOR_ID))) {
                assertThat(page).containsExactly(first);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link ActorRepository#findAllByLastNameOrderByActorIdAsc(String)} ({@code SAKILA-ACTOR-B-01}).
     */
    @Nested
    class FindAllByLastNameOrderByActorIdAsc_Test {

        private static Stream<String> lastNames() {
            return ActorRepository_SpringBootIT.lastNames();
        }

        /**
         * Asserts that the exercise's last name, {@code GUINESS}, finds the actors {@code 1}, {@code 90} and
         * {@code 179}, in that order, as the exercise's expected output shows.
         */
        @Test
        void __Exercise() {
            final var found = repositoryInstance().findAllByLastNameOrderByActorIdAsc("GUINESS");
            assertThat(found)
                    .extracting(Actor::getActorId, Actor::getFirstName)
                    .containsExactly(tuple(1, "PENELOPE"), tuple(90, "SEAN"), tuple(179, "ED"));
        }

        /**
         * Asserts that the last name of a selected actor finds actors of that name only, with the selected actor among
         * them, by id as the exercise sorts.
         *
         * @param lastName the last name of one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("lastNames")
        @ParameterizedTest
        void __(final String lastName) {
            final var found = repositoryInstance().findAllByLastNameOrderByActorIdAsc(lastName);
            assertThat(found)
                    .isNotEmpty()
                    .allSatisfy(a -> assertThat(a.getLastName()).isEqualToIgnoringCase(lastName))
                    .isSortedAccordingTo(Comparator.comparing(Actor::getActorId));
            assertThat(found).extracting(Actor::getActorId)
                    .containsAnyElementsOf(entities.stream().map(Actor::getActorId).toList());
        }
    }

    /**
     * Tests {@link ActorRepository#findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(String)}
     * ({@code SAKILA-ACTOR-B-02}).
     */
    @Nested
    class FindAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc_Test {

        private static Stream<String> prefixes() {
            return firstNamePrefixes();
        }

        /**
         * Asserts, in one transaction, that the exercise's prefix, {@code JU}, streams the seven actors of the
         * exercise's expected output, in its order.
         */
        @Test
        @Transactional
        void __Exercise() {
            try (var found = repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc("JU")) {
                assertThat(found.toList())
                        .extracting(Actor::getActorId)
                        .containsExactly(57, 35, 27, 47, 186, 199, 123);
            }
        }

        /**
         * Asserts, in one transaction, that the first two letters of a selected actor's first name stream actors whose
         * first name starts so, by first name and then by id as the exercise sorts.
         *
         * @param prefix the first two letters of the first name of one of the selected actors.
         */
        @MethodSource("prefixes")
        @ParameterizedTest
        @Transactional
        void __Transactional(final String prefix) {
            try (var found = repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(prefix)) {
                assertThat(found.toList())
                        .isNotEmpty()
                        .allSatisfy(a -> assertThat(a.getFirstName()).startsWithIgnoringCase(prefix))
                        .isSortedAccordingTo(Comparator.comparing(Actor::getFirstName)
                                                     .thenComparing(Actor::getActorId));
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         *
         * @param prefix the first two letters of the first name of one of the selected actors.
         */
        @MethodSource("prefixes")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final String prefix) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(
                        prefix)) {
                    found.forEach(a -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link ActorRepository#selectLastNamesSharedByMoreThan(long, Pageable)} ({@code SAKILA-ACTOR-I-01}).
     */
    @Nested
    class SelectLastNamesSharedByMoreThan_Test {

        private static final Sort BY_ACTORS_DESC_THEN_LAST_NAME =
                Sort.by(Sort.Direction.DESC, "actors").and(Sort.by("lastName"));

        /**
         * Asserts that the exercise's threshold, three, selects {@code KILMER}, {@code NOLTE} and {@code TEMPLE}, with
         * their counts, in the exercise's order, three in all, as the exercise's expected output shows.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectLastNamesSharedByMoreThan(
                    3L, PageRequest.of(0, 10, BY_ACTORS_DESC_THEN_LAST_NAME));
            assertThat(page.getContent())
                    .extracting(LastNameCount::getLastName, LastNameCount::getActors)
                    .containsExactly(tuple("KILMER", 5L), tuple("NOLTE", 4L), tuple("TEMPLE", 4L));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        /**
         * Asserts that every name selected is shared by more than the threshold, that the page is ordered by the
         * count and then by the name as the exercise sorts, and that each count is the number of actors of that name.
         *
         * @param actors the threshold.
         */
        @ValueSource(longs = {1L, 3L, 5L})
        @ParameterizedTest
        void __(final long actors) {
            final var page = repositoryInstance().selectLastNamesSharedByMoreThan(
                    actors, PageRequest.of(0, 5, BY_ACTORS_DESC_THEN_LAST_NAME));
            assertPage(page);
            assertThat(page.getContent())
                    .allSatisfy(c -> assertThat(c.getActors()).isGreaterThan(actors))
                    .isSortedAccordingTo(Comparator.comparingLong(LastNameCount::getActors).reversed()
                                                 .thenComparing(LastNameCount::getLastName))
                    .allSatisfy(c -> {
                        final var named = repositoryInstance().findAllByLastNameOrderByActorIdAsc(c.getLastName());
                        assertThat((long) named.size()).as("actors named %s", c.getLastName())
                                .isEqualTo(c.getActors());
                    });
        }
    }

    /**
     * Tests {@link ActorRepository#selectFilmCounts(Pageable)} ({@code SAKILA-ACTOR-I-02}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_ACTOR_ID =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("actorId"));

        /**
         * Asserts that the first page of five, the most filmed first, then by id, holds the five actors of the
         * exercise's expected output, with their counts.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, 5, BY_FILMS_DESC_THEN_ACTOR_ID));
            assertThat(page.getContent())
                    .extracting(ActorFilmCount::getActorId, ActorFilmCount::getFilms)
                    .containsExactly(tuple(107, 42L), tuple(102, 41L), tuple(198, 40L), tuple(181, 39L),
                                     tuple(23, 37L));
        }

        /**
         * Asserts that a page of the given size, the most filmed first as the exercise sorts, is ordered so, and that
         * each count is the number of the actor's {@code film_actor} links.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectFilmCounts(
                    PageRequest.of(0, size, BY_FILMS_DESC_THEN_ACTOR_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparingLong(ActorFilmCount::getFilms).reversed()
                                                 .thenComparing(ActorFilmCount::getActorId))
                    .allSatisfy(c -> {
                        final var actor = repositoryInstance().findById(c.getActorId()).orElseThrow();
                        assertThat(actor.getFirstName()).isEqualTo(c.getFirstName());
                        assertThat(actor.getLastName()).isEqualTo(c.getLastName());
                        assertThat(filmActorsOf(actor)).hasSize((int) c.getFilms());
                    });
        }
    }

    /**
     * Tests {@link ActorRepository#listInEveryRating(Pageable)} ({@code SAKILA-ACTOR-A-01}).
     */
    @Nested
    class ListInEveryRating_Test {

        /**
         * Asserts that the first page of five, by id, holds the actors {@code 1} to {@code 5}, as the exercise's
         * expected output shows.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().listInEveryRating(PageRequest.of(0, 5, BY_ACTOR_ID));
            assertThat(page.getContent()).extracting(Actor::getActorId).containsExactly(1, 2, 3, 4, 5);
        }

        /**
         * Asserts, in one transaction, that a page of the given size by id holds actors whose films cover every
         * rating the sample data uses, which is all five.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        @Transactional
        void __(final int size) {
            final var page = repositoryInstance().listInEveryRating(PageRequest.of(0, size, BY_ACTOR_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(Actor::getActorId))
                    .allSatisfy(a -> {
                        final var ratings = filmActorsOf(a).stream()
                                .map(FilmActor::getFilm)
                                .map(Film::getRating)
                                .collect(Collectors.toSet());
                        assertThat(ratings).isEqualTo(EnumSet.allOf(Film.Rating.class));
                    });
        }
    }

    /**
     * Tests {@link ActorRepository#selectLongestFilmsOfActorIdsBetween(int, int, Pageable)}
     * ({@code SAKILA-ACTOR-A-03}).
     */
    @Nested
    class SelectLongestFilmsOfActorIdsBetween_Test {

        private static final Sort BY_ACTOR_ID_THEN_TITLE = Sort.by("actorId", "title");

        private static Stream<Actor> actors() {
            return ActorRepository_SpringBootIT.actors();
        }

        /**
         * Asserts that the exercise's range, the actors {@code 1} to {@code 5}, by id and then by title, selects the
         * five films of the exercise's expected output.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    1, 5, Pageable.unpaged(BY_ACTOR_ID_THEN_TITLE));
            assertThat(page.getContent())
                    .extracting(ActorLongestFilm::getActorId, ActorLongestFilm::getTitle,
                                ActorLongestFilm::getLength)
                    .containsExactly(
                            tuple(1, "KING EVOLUTION", 184),
                            tuple(2, "WARDROBE PHANTOM", 178),
                            tuple(3, "YOUNG LANGUAGE", 183),
                            tuple(4, "REDS POCUS", 182),
                            tuple(5, "SOLDIERS EVOLUTION", 185)
                    );
        }

        /**
         * Asserts, in one transaction, that the range of a selected actor alone selects the actor's films of the
         * longest length the actor's {@code film_actor} links reach, by title, with the actor's names.
         *
         * @param actor one of the actors selected in {@link ActorRepository_SpringBootIT#__()}.
         */
        @MethodSource("actors")
        @ParameterizedTest
        @Transactional
        void __(final Actor actor) {
            final var films = filmActorsOf(actor).stream().map(FilmActor::getFilm).toList();
            final var longest = films.stream().map(Film::getLength).max(Comparator.naturalOrder()).orElseThrow();
            final var expected = films.stream()
                    .filter(f -> f.getLength().equals(longest))
                    .map(Film::getTitle)
                    .sorted()
                    .toList();
            final var page = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    actor.getActorId(), actor.getActorId(), Pageable.unpaged(BY_ACTOR_ID_THEN_TITLE));
            assertThat(page.getContent())
                    .allSatisfy(r -> {
                        assertThat(r.getActorId()).isEqualTo(actor.getActorId());
                        assertThat(r.getFirstName()).isEqualTo(actor.getFirstName());
                        assertThat(r.getLastName()).isEqualTo(actor.getLastName());
                        assertThat(r.getLength()).isEqualTo(longest);
                    })
                    .extracting(ActorLongestFilm::getTitle)
                    .containsExactlyElementsOf(expected);
        }

        /**
         * Asserts that pages of two over the exercise's range, by id and then by title, walk the same rows as the
         * whole list, in the same order, as many in all.
         */
        @Test
        void __Paged() {
            final var all = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    1, 5, Pageable.unpaged(BY_ACTOR_ID_THEN_TITLE)).getContent();
            var page = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    1, 5, PageRequest.of(0, 2, BY_ACTOR_ID_THEN_TITLE));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(all.size());
            final var walked = new ArrayList<String>();
            while (true) {
                page.getContent().forEach(r -> walked.add(r.getActorId() + "/" + r.getTitle()));
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().selectLongestFilmsOfActorIdsBetween(1, 5, page.nextPageable());
            }
            assertThat(walked).containsExactlyElementsOf(
                    all.stream().map(r -> r.getActorId() + "/" + r.getTitle()).toList());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmActorRepository filmActorRepository;
}
