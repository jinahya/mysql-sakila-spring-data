package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Actor;
import com.github.jinahya.mysql.sakila.persistence.Actor_;
import com.github.jinahya.mysql.sakila.persistence.Film;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link ActorRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the actors it needs, with the names it needs, and
 * the slice rolls them back when it ends. The names are random, and unique to the test, so a test can count what it
 * finds, whatever else the H2 of the cached context may hold.
 *
 * @see ActorRepository_SpringBootIT
 */
class ActorRepository_DataJpaTest
        extends _Repository_DataJpaTest<ActorRepository, Actor, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Actor_.actorId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_ACTOR_ID = Sort.by(Sort.Direction.ASC, Actor_.ACTOR_ID);

    /**
     * Creates a new instance.
     */
    ActorRepository_DataJpaTest() {
        super(ActorRepository.class, Actor.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Saves a randomized actor with the specified names, through the repository under test.
     * <p>
     * The id is {@code IDENTITY}, so the insert runs at once, and the actor returned has its id; the ids of successive
     * calls ascend.
     *
     * @param firstName the first name of the actor.
     * @param lastName  the last name of the actor.
     * @return the saved actor, managed in the test's persistence context.
     */
    private Actor saveActor(final String firstName, final String lastName) {
        final var actor = _Repository_DataJpaTest_Fixtures.randomized(Actor.class);
        actor.setFirstName(firstName);
        actor.setLastName(lastName);
        return repositoryInstance().save(actor);
    }

    /**
     * Saves a film with the specified rating, in a language of its own.
     *
     * @param rating the rating of the film.
     * @return the saved film.
     */
    private Film saveFilm(final Film.Rating rating) {
        final var film = fixtures().film();
        film.setRating(rating);
        return film;
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link ActorRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The id is drawn from the whole {@code int} range, so it is all but certain to miss the small identity values
         * the H2 hands out.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves an actor, and asserts that finding it by its id returns that very instance.
         * <p>
         * {@code findById} is {@code EntityManager.find}, which answers from the test's persistence context, where the
         * actor is already managed, without going to the database.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = saveActor(randomName(), randomName());
            final var found = repositoryInstance().findById(saved.getActorId());
            assertThat(found).hasValueSatisfying(v -> {
                assertThat(v).isSameAs(saved);
            });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link ActorRepository#findAllByFirstName(String, Pageable)}.
     */
    @Nested
    class FindAllByFirstName_Test {

        /**
         * Asserts that finding by a first name no actor has returns an empty list.
         */
        @Test
        void _Empty_Unknown() {
            final var found = repositoryInstance().findAllByFirstName(randomName(), Pageable.unpaged());
            assertThat(found).isEmpty();
        }

        /**
         * Saves three actors sharing a first name, and one with another, and asserts that the name finds the three,
         * and those very instances.
         * <p>
         * The query does go to the database, but the test's transaction holds one persistence context, and a row whose
         * id is already managed there resolves to the managed instance; hence the same instances, not just equal ones.
         */
        @Test
        void _NotEmpty_Known() {
            final var firstName = randomName();
            final var actor1 = saveActor(firstName, randomName());
            final var actor2 = saveActor(firstName, randomName());
            final var actor3 = saveActor(firstName, randomName());
            final var other = saveActor(randomName(), randomName());
            final var found = repositoryInstance().findAllByFirstName(firstName, Pageable.unpaged());
            assertThat(found)
                    .containsExactlyInAnyOrder(actor1, actor2, actor3)
                    .doesNotContain(other)
                    .allSatisfy(a -> {
                        assertThat(a.getFirstName()).isEqualTo(firstName);
                    });
            assertThat(found).anySatisfy(a -> assertThat(a).isSameAs(actor1));
        }

        /**
         * Saves three actors sharing a first name, and asserts that pages of two, sorted by id, split them two and
         * one, in the order they were saved.
         */
        @Test
        void __Paged() {
            final var firstName = randomName();
            final var actor1 = saveActor(firstName, randomName());
            final var actor2 = saveActor(firstName, randomName());
            final var actor3 = saveActor(firstName, randomName());
            final var page0 = repositoryInstance().findAllByFirstName(firstName, PageRequest.of(0, 2, BY_ACTOR_ID));
            assertThat(page0).containsExactly(actor1, actor2);
            final var page1 = repositoryInstance().findAllByFirstName(firstName, PageRequest.of(1, 2, BY_ACTOR_ID));
            assertThat(page1).containsExactly(actor3);
        }

        /**
         * Runs the same lookup as a {@link Specification}, and asserts that it finds the same actors.
         * <p>
         * A query method is not the only way to a query: {@link ActorRepository} extends
         * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}, so a criteria predicate built by
         * the caller works whatever methods the repository declares. The attribute is named through the generated
         * {@link Actor_#firstName} metamodel, not by string.
         */
        @Test
        void __CriteriaApi() {
            final var firstName = randomName();
            final var actor1 = saveActor(firstName, randomName());
            final var actor2 = saveActor(firstName, randomName());
            final Specification<Actor> specification =
                    (r, q, b) -> b.equal(r.get(Actor_.firstName), firstName);
            final var found = repositoryInstance().findAll(specification);
            assertThat(found).containsExactlyInAnyOrder(actor1, actor2);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link ActorRepository#findAllByLastName(String, Pageable)}.
     */
    @Nested
    class FindAllByLastName_Test {

        /**
         * Asserts that finding by a last name no actor has returns an empty stream.
         */
        @Test
        void _Empty_Unknown() {
            try (var found = repositoryInstance().findAllByLastName(randomName(), Pageable.unpaged())) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Saves three actors sharing a last name, and one with another, and asserts that the name streams the three,
         * and those very instances.
         */
        @Test
        void _NotEmpty_Known() {
            final var lastName = randomName();
            final var actor1 = saveActor(randomName(), lastName);
            final var actor2 = saveActor(randomName(), lastName);
            final var actor3 = saveActor(randomName(), lastName);
            final var other = saveActor(randomName(), randomName());
            try (var found = repositoryInstance().findAllByLastName(lastName, Pageable.unpaged())) {
                final var list = found.toList();
                assertThat(list)
                        .containsExactlyInAnyOrder(actor1, actor2, actor3)
                        .doesNotContain(other)
                        .allSatisfy(a -> {
                            assertThat(a.getLastName()).isEqualTo(lastName);
                        });
                assertThat(list).anySatisfy(a -> assertThat(a).isSameAs(actor1));
            }
        }

        /**
         * Saves three actors sharing a last name, and asserts that a page of two, sorted by id descending, streams
         * the last two saved, newest first.
         */
        @Test
        void __Paged() {
            final var lastName = randomName();
            saveActor(randomName(), lastName);
            final var actor2 = saveActor(randomName(), lastName);
            final var actor3 = saveActor(randomName(), lastName);
            final var pageable = PageRequest.of(0, 2, JpaSort.of(Sort.Direction.DESC, Actor_.actorId));
            try (var found = repositoryInstance().findAllByLastName(lastName, pageable)) {
                assertThat(found).containsExactly(actor3, actor2);
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException} whether or not any actor has the name.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByLastName(randomName(), Pageable.unpaged())) {
                    found.forEach(a -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link ActorRepository#findAllByLastNameOrderByActorIdAsc(String)} ({@code SAKILA_ACTOR_B_01}).
     */
    @Nested
    class FindAllByLastNameOrderByActorIdAsc_Test {

        /**
         * Saves three actors sharing a last name, and one with another, and asserts that the name finds the three, and
         * those very instances.
         */
        @Test
        void __() {
            final var lastName = randomName();
            final var actor1 = saveActor(randomName(), lastName);
            final var actor2 = saveActor(randomName(), lastName);
            final var actor3 = saveActor(randomName(), lastName);
            final var other = saveActor(randomName(), randomName());
            final var found = repositoryInstance().findAllByLastNameOrderByActorIdAsc(lastName);
            assertThat(found)
                    .containsExactlyInAnyOrder(actor1, actor2, actor3)
                    .doesNotContain(other)
                    .allSatisfy(a -> assertThat(a.getLastName()).isEqualTo(lastName));
            assertThat(found).anySatisfy(a -> assertThat(a).isSameAs(actor1));
        }

        /**
         * Saves three actors sharing a last name, and asserts that they are found by id, as the exercise sorts, which
         * is the order they were saved in.
         */
        @Test
        void __Ordered() {
            final var lastName = randomName();
            final var actor1 = saveActor(randomName(), lastName);
            final var actor2 = saveActor(randomName(), lastName);
            final var actor3 = saveActor(randomName(), lastName);
            final var found = repositoryInstance().findAllByLastNameOrderByActorIdAsc(lastName);
            assertThat(found).containsExactly(actor1, actor2, actor3);
        }

        /**
         * Asserts that a last name no actor has finds an empty list.
         */
        @Test
        void _Empty_Unknown() {
            final var found = repositoryInstance().findAllByLastNameOrderByActorIdAsc(randomName());
            assertThat(found).isEmpty();
        }

        /**
         * Asserts that a {@code null} last name is refused with {@link IllegalArgumentException}, before any query
         * runs: the package is {@code @NullMarked}, and Spring Data checks the arguments of a repository method so.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().findAllByLastNameOrderByActorIdAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link ActorRepository#findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(String)}
     * ({@code SAKILA_ACTOR_B_02}).
     */
    @Nested
    class FindAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc_Test {

        /**
         * Saves actors whose first names share a prefix, and one whose does not, and asserts that the prefix streams the
         * former, by first name and then by id, as the exercise sorts.
         * <p>
         * The test's transaction surrounds the call, so the stream can be consumed.
         */
        @Test
        void __() {
            final var prefix = randomName();
            final var b1 = saveActor(prefix + 'b', randomName());
            final var a2 = saveActor(prefix + 'a', randomName());
            final var a3 = saveActor(prefix + 'a', randomName());
            final var other = saveActor(randomName(), randomName());
            try (var found = repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(prefix)) {
                assertThat(found.toList())
                        .containsExactly(a2, a3, b1)
                        .doesNotContain(other)
                        .allSatisfy(a -> assertThat(a.getFirstName()).startsWith(prefix));
            }
        }

        /**
         * Asserts that a prefix no first name starts with streams nothing.
         */
        @Test
        void _Empty_Unknown() {
            try (var found = repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(
                    randomName())) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it, before any
         * query runs, as it does for {@link ActorRepository#findAllByLastName(String, Pageable)}.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(
                        randomName())) {
                    found.forEach(a -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} prefix is refused with {@link IllegalArgumentException}, before any query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().findAllByFirstNameStartingWithOrderByFirstNameAscActorIdAsc(
                    null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link ActorRepository#selectLastNamesSharedByMoreThan(long, Pageable)} ({@code SAKILA_ACTOR_I_01}).
     */
    @Nested
    class SelectLastNamesSharedByMoreThan_Test {

        private static final Sort BY_ACTORS_DESC_THEN_LAST_NAME =
                Sort.by(Sort.Direction.DESC, "actors").and(Sort.by("lastName"));

        /**
         * Saves four actors sharing one last name and two sharing another, and asserts that a threshold of three
         * keeps the first name only, and a threshold of one keeps both, the more shared first, two in all, which the
         * count query says even when the page holds one.
         */
        @Test
        void __() {
            final var four = randomName();
            final var two = randomName();
            for (int i = 0; i < 4; i++) {
                saveActor(randomName(), four);
            }
            for (int i = 0; i < 2; i++) {
                saveActor(randomName(), two);
            }
            final var aboveThree = repositoryInstance().selectLastNamesSharedByMoreThan(
                    3L, PageRequest.of(0, 5, BY_ACTORS_DESC_THEN_LAST_NAME));
            assertThat(aboveThree.getContent())
                    .extracting(column("lastName", String.class), column("actors", Long.class))
                    .containsExactly(tuple(four, 4L));
            assertThat(aboveThree.getTotalElements()).isEqualTo(1L);
            final var aboveOne = repositoryInstance().selectLastNamesSharedByMoreThan(
                    1L, PageRequest.of(0, 1, BY_ACTORS_DESC_THEN_LAST_NAME));
            assertThat(aboveOne.getContent())
                    .extracting(column("lastName", String.class), column("actors", Long.class))
                    .containsExactly(tuple(four, 4L));
            assertThat(aboveOne.getTotalElements()).isEqualTo(2L);
            assertThat(aboveOne.getTotalPages()).isEqualTo(2);
            final var next = repositoryInstance().selectLastNamesSharedByMoreThan(1L, aboveOne.nextPageable());
            assertThat(next.getContent())
                    .extracting(column("lastName", String.class), column("actors", Long.class))
                    .containsExactly(tuple(two, 2L));
        }
    }

    /**
     * Tests {@link ActorRepository#selectFilmCounts(Pageable)} ({@code SAKILA_ACTOR_I_02}).
     */
    @Nested
    class SelectFilmCounts_Test {

        /**
         * Saves actors in three, two, one and no films, and asserts that a page of two, in the query's own order,
         * holds the first two, with their counts, that the actors with a film are three in all, and that the actor in
         * no film is never selected.
         */
        @Test
        void __() {
            final var film1 = fixtures().film();
            final var film2 = fixtures().film();
            final var film3 = fixtures().film();
            final var three = saveActor(randomName(), randomName());
            final var two = saveActor(randomName(), randomName());
            final var one = saveActor(randomName(), randomName());
            final var none = saveActor(randomName(), randomName());
            for (final var film : new Film[] {film1, film2, film3}) {
                fixtures().filmActor(three, film);
            }
            for (final var film : new Film[] {film1, film2}) {
                fixtures().filmActor(two, film);
            }
            fixtures().filmActor(one, film1);
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, 2));
            assertThat(page.getContent())
                    .extracting(column("actor", Actor.class, Actor::getActorId),
                                column("actor", Actor.class, Actor::getFirstName),
                                column("actor", Actor.class, Actor::getLastName),
                                column("films", Long.class))
                    .containsExactly(
                            tuple(three.getActorId(), three.getFirstName(), three.getLastName(), 3L),
                            tuple(two.getActorId(), two.getFirstName(), two.getLastName(), 2L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(3L);
            final var all = repositoryInstance().selectFilmCounts(Pageable.unpaged());
            assertThat(all.getContent())
                    .noneMatch(c -> c.get("actor", Actor.class).getActorId().equals(none.getActorId()))
                    .extracting(column("actor", Actor.class, Actor::getActorId), column("films", Long.class))
                    .endsWith(tuple(one.getActorId(), 1L));
        }
    }

    /**
     * Tests {@link ActorRepository#listInEveryRating(Pageable)} ({@code SAKILA_ACTOR_A_01}).
     */
    @Nested
    class ListInEveryRating_Test {

        /**
         * Saves a film of each of two ratings, an actor in both and an actor in one, and asserts that only the former
         * is listed.
         * <p>
         * The ratings of the two films are the only ratings in the H2, so two distinct ratings is every rating.
         */
        @Test
        void __() {
            final var g = saveFilm(Film.Rating.G);
            final var pg = saveFilm(Film.Rating.PG);
            final var both = saveActor(randomName(), randomName());
            fixtures().filmActor(both, g);
            fixtures().filmActor(both, pg);
            final var one = saveActor(randomName(), randomName());
            fixtures().filmActor(one, g);
            final var page = repositoryInstance().listInEveryRating(Pageable.unpaged(BY_ACTOR_ID));
            assertThat(page.getContent())
                    .contains(both)
                    .doesNotContain(one);
        }
    }

    /**
     * Tests {@link ActorRepository#selectLongestFilmsOfActorIdsBetween(int, int, Pageable)}
     * ({@code SAKILA_ACTOR_A_03}).
     */
    @Nested
    class SelectLongestFilmsOfActorIdsBetween_Test {

        private static final Sort BY_ACTOR_ID_THEN_TITLE = Sort.by("a.actorId", "f.title");

        /**
         * Saves an actor in films of {@code 100}, {@code 120} and {@code 120} minutes, and another in one film, and
         * asserts that the range of the two lists both films of {@code 120} minutes of the first, by title, then the
         * one film of the second, and that a range of the first alone leaves the second out.
         */
        @Test
        void __() {
            final var first = saveActor(randomName(), randomName());
            final var second = saveActor(randomName(), randomName());
            final var shorter = saveFilm(Film.Rating.G);
            shorter.setLength(100);
            final var longest1 = saveFilm(Film.Rating.G);
            longest1.setLength(120);
            longest1.setTitle(randomName());
            final var longest2 = saveFilm(Film.Rating.G);
            longest2.setLength(120);
            longest2.setTitle(randomName());
            final var only = saveFilm(Film.Rating.G);
            only.setLength(90);
            for (final var film : new Film[] {shorter, longest1, longest2}) {
                fixtures().filmActor(first, film);
            }
            fixtures().filmActor(second, only);
            final var titles = Stream.of(longest1, longest2).map(Film::getTitle).sorted().toList();
            final var page = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    first.getActorId(), second.getActorId(), Pageable.unpaged(BY_ACTOR_ID_THEN_TITLE));
            assertThat(page.getContent())
                    .extracting(column("actor", Actor.class, Actor::getActorId),
                                column("film", Film.class, Film::getTitle),
                                column("film", Film.class, Film::getLength))
                    .containsExactly(
                            tuple(first.getActorId(), titles.get(0), 120),
                            tuple(first.getActorId(), titles.get(1), 120),
                            tuple(second.getActorId(), only.getTitle(), 90)
                    );
            final var firstOnly = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    first.getActorId(), first.getActorId(), Pageable.unpaged(BY_ACTOR_ID_THEN_TITLE));
            assertThat(firstOnly.getContent())
                    .extracting(column("actor", Actor.class, Actor::getActorId))
                    .containsOnly(first.getActorId());
        }

        /**
         * Saves an actor in three films of one length, and asserts that pages of two by title split them two and one,
         * three in all, which the count query says.
         */
        @Test
        void __Paged() {
            final var actor = saveActor(randomName(), randomName());
            for (int i = 0; i < 3; i++) {
                final var film = saveFilm(Film.Rating.G);
                film.setLength(150);
                fixtures().filmActor(actor, film);
            }
            final var page0 = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    actor.getActorId(), actor.getActorId(), PageRequest.of(0, 2, BY_ACTOR_ID_THEN_TITLE));
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isEqualTo(3L);
            final var page1 = repositoryInstance().selectLongestFilmsOfActorIdsBetween(
                    actor.getActorId(), actor.getActorId(), page0.nextPageable());
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} pageable is refused with {@link IllegalArgumentException}, before any query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().selectLongestFilmsOfActorIdsBetween(1, 5, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
