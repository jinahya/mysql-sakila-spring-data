package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.ActorRepository.ActorFilmCount;
import com.github.jinahya.mysql.sakila.data.ActorRepository.LastNameCount;
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
     * Tests {@link ActorRepository#listByLastName(String, Pageable)} ({@code SAKILA-ACTOR-B-01}).
     */
    @Nested
    class ListByLastName_Test {

        /**
         * Asserts that a last name no actor has lists an empty page, with no elements in all.
         */
        @Test
        void _Empty_Unknown() {
            final var page = repositoryInstance().listByLastName(randomName(), PageRequest.of(0, 5, BY_ACTOR_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves three actors sharing a last name, and one with another, and asserts that pages of two by id hold the
         * first two and then the third, three in all.
         */
        @Test
        void __() {
            final var lastName = randomName();
            final var actor1 = saveActor(randomName(), lastName);
            final var actor2 = saveActor(randomName(), lastName);
            final var actor3 = saveActor(randomName(), lastName);
            final var other = saveActor(randomName(), randomName());
            final var page0 = repositoryInstance().listByLastName(lastName, PageRequest.of(0, 2, BY_ACTOR_ID));
            assertThat(page0.getContent()).containsExactly(actor1, actor2).doesNotContain(other);
            assertThat(page0.getTotalElements()).isEqualTo(3L);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            final var page1 = repositoryInstance().listByLastName(lastName, page0.nextPageable());
            assertThat(page1.getContent()).containsExactly(actor3);
            assertThat(page1.hasNext()).isFalse();
        }
    }

    /**
     * Tests {@link ActorRepository#listByFirstNameStartingWith(String, Pageable)} ({@code SAKILA-ACTOR-B-02}).
     */
    @Nested
    class ListByFirstNameStartingWith_Test {

        /**
         * Asserts that a prefix no first name starts with lists an empty page.
         */
        @Test
        void _Empty_Unknown() {
            final var page = repositoryInstance().listByFirstNameStartingWith(randomName(), Pageable.unpaged());
            assertThat(page).isEmpty();
        }

        /**
         * Saves actors whose first names share a prefix, and one whose does not, and asserts that the prefix lists
         * the former, by first name and then by id, as the exercise sorts.
         */
        @Test
        void __() {
            final var prefix = randomName();
            final var b1 = saveActor(prefix + 'b', randomName());
            final var a2 = saveActor(prefix + 'a', randomName());
            final var a3 = saveActor(prefix + 'a', randomName());
            final var other = saveActor(randomName(), randomName());
            final var sort = JpaSort.of(Sort.Direction.ASC, Actor_.firstName, Actor_.actorId);
            final var page = repositoryInstance().listByFirstNameStartingWith(prefix, Pageable.unpaged(sort));
            assertThat(page.getContent())
                    .containsExactly(a2, a3, b1)
                    .doesNotContain(other)
                    .allSatisfy(a -> assertThat(a.getFirstName()).startsWith(prefix));
            assertThat(page.getTotalElements()).isEqualTo(3L);
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
                    .extracting(LastNameCount::getLastName, LastNameCount::getActors)
                    .containsExactly(tuple(four, 4L));
            assertThat(aboveThree.getTotalElements()).isEqualTo(1L);
            final var aboveOne = repositoryInstance().selectLastNamesSharedByMoreThan(
                    1L, PageRequest.of(0, 1, BY_ACTORS_DESC_THEN_LAST_NAME));
            assertThat(aboveOne.getContent())
                    .extracting(LastNameCount::getLastName, LastNameCount::getActors)
                    .containsExactly(tuple(four, 4L));
            assertThat(aboveOne.getTotalElements()).isEqualTo(2L);
            assertThat(aboveOne.getTotalPages()).isEqualTo(2);
            final var next = repositoryInstance().selectLastNamesSharedByMoreThan(1L, aboveOne.nextPageable());
            assertThat(next.getContent())
                    .extracting(LastNameCount::getLastName, LastNameCount::getActors)
                    .containsExactly(tuple(two, 2L));
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
         * Saves actors in three, two, one and no films, and asserts that a page of two by films holds the first two,
         * with their counts, that the actors with a film are three in all, and that the actor in no film is never
         * selected.
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
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, 2, BY_FILMS_DESC_THEN_ACTOR_ID));
            assertThat(page.getContent())
                    .extracting(ActorFilmCount::getActorId, ActorFilmCount::getFirstName, ActorFilmCount::getLastName,
                                ActorFilmCount::getFilms)
                    .containsExactly(
                            tuple(three.getActorId(), three.getFirstName(), three.getLastName(), 3L),
                            tuple(two.getActorId(), two.getFirstName(), two.getLastName(), 2L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(3L);
            final var all = repositoryInstance().selectFilmCounts(Pageable.unpaged(BY_FILMS_DESC_THEN_ACTOR_ID));
            assertThat(all.getContent())
                    .noneMatch(c -> c.getActorId().equals(none.getActorId()))
                    .extracting(ActorFilmCount::getActorId, ActorFilmCount::getFilms)
                    .endsWith(tuple(one.getActorId(), 1L));
        }
    }

    /**
     * Tests {@link ActorRepository#listInEveryRating(Pageable)} ({@code SAKILA-ACTOR-A-01}).
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
}
