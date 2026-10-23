package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.ActorInfo;
import com.github.jinahya.mysql.sakila.persistence.ActorInfo_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link ActorInfoRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rows of the
 * view, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against the
 * {@code actor} table the view is built on, read through {@link ActorRepository}. The base's own tests, such as its
 * {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see ActorInfoRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActorInfoRepository_SpringBootIT
        extends _Repository_SpringBootIT<ActorInfoRepository, ActorInfo, Integer> {

    private static List<ActorInfo> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code ActorInfo_.actorId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_ACTOR_ID = Sort.by(Sort.Direction.ASC, ActorInfo_.ACTOR_ID);

    /**
     * The number of leading characters of the {@code film_info} the exercise shows.
     */
    private static final int FILM_INFO_LENGTH = 80;

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    ActorInfoRepository_SpringBootIT() {
        super(ActorInfoRepository.class, ActorInfo.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rows of the view, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones of the first actors the init scripts put there. Runs once per
     * class, which {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired
     * repository is already there; the result goes into a static field so that the static method sources of the
     * nested classes can reach it. The rows are loaded outside any transaction, so they are detached.
     * <p>
     * An empty view aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_ACTOR_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<ActorInfo> actorInfos() {
        return entities.stream();
    }

    /**
     * Returns the first characters of the specified string, as many as the exercise shows, or the whole string if it
     * is shorter.
     *
     * @param filmInfo the string; may be {@code null}.
     * @return the head of {@code filmInfo}; {@code null} if {@code filmInfo} is {@code null}.
     */
    private static String head(final String filmInfo) {
        if (filmInfo == null) {
            return null;
        }
        return filmInfo.substring(0, Math.min(FILM_INFO_LENGTH, filmInfo.length()));
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link ActorInfoRepository#selectFilmInfoHeadByActorId(int, int)} ({@code SAKILA-ACTOR_INFO-B-01}).
     */
    @Nested
    class SelectFilmInfoHeadByActorId_Test {

        private static Stream<ActorInfo> actorInfos() {
            return ActorInfoRepository_SpringBootIT.actorInfos();
        }

        /**
         * Asserts that the id of a selected row selects that row, with the names the row has, which are the names
         * of the actor of that id in the {@code actor} table, and with the first eighty characters of the row's
         * {@code film_info}.
         *
         * @param actorInfo one of the rows selected in {@link ActorInfoRepository_SpringBootIT#__()}.
         */
        @MethodSource("actorInfos")
        @ParameterizedTest
        void __(final ActorInfo actorInfo) {
            final var actorId = actorInfo.getActorId();
            final var selected = repositoryInstance().selectFilmInfoHeadByActorId(actorId, FILM_INFO_LENGTH);
            assertThat(selected).hasValueSatisfying(h -> {
                assertThat(h.getActorId()).isEqualTo(actorId);
                assertThat(h.getFirstName()).isEqualTo(actorInfo.getFirstName());
                assertThat(h.getLastName()).isEqualTo(actorInfo.getLastName());
                assertThat(h.getFilmInfo()).isEqualTo(head(actorInfo.getFilmInfo()));
                final var actor = actorRepository.findById(actorId).orElseThrow();
                assertThat(h.getFirstName()).isEqualToIgnoringCase(actor.getFirstName());
                assertThat(h.getLastName()).isEqualToIgnoringCase(actor.getLastName());
            });
        }

        /**
         * Asserts that an id no actor has selects nothing.
         * <p>
         * The ids of the sample data are positive, so a negative one misses.
         */
        @ValueSource(ints = {-1, Integer.MIN_VALUE})
        @ParameterizedTest
        void _Empty_Unknown(final int actorId) {
            final var selected = repositoryInstance().selectFilmInfoHeadByActorId(actorId, FILM_INFO_LENGTH);
            assertThat(selected).isEmpty();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#countByFilmInfoCategory(String)} ({@code SAKILA-ACTOR_INFO-B-02}).
     */
    @Nested
    class CountByFilmInfoCategory_Test {

        /**
         * Asserts that the count of a category is the number of rows of the view whose {@code film_info} names it,
         * read as the whole view and filtered here, and that it is positive for {@code Horror}, which the exercise
         * counts, and the other categories the sample data has.
         *
         * @param category the name of the category.
         */
        @ValueSource(strings = {"Horror", "Animation", "Comedy"})
        @ParameterizedTest
        void __(final String category) {
            final var count = repositoryInstance().countByFilmInfoCategory(category);
            assertThat(count).isPositive();
            final var expected = repositoryInstance().findAll(Pageable.unpaged()).stream()
                    .map(ActorInfo::getFilmInfo)
                    .filter(i -> i != null && i.contains(category + ':'))
                    .count();
            assertThat(count).isEqualTo(expected);
        }

        /**
         * Asserts that a name no category has counts zero.
         */
        @ValueSource(strings = {"Nonesuch"})
        @ParameterizedTest
        void _Zero_Unknown(final String category) {
            assertThat(repositoryInstance().countByFilmInfoCategory(category)).isZero();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#selectRowCounts()} ({@code SAKILA-ACTOR_INFO-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        private static Stream<ActorInfo> actorInfos() {
            return ActorInfoRepository_SpringBootIT.actorInfos();
        }

        /**
         * Asserts that the view has as many rows as the {@code actor} table, as the exercise checks, and that each
         * count is what the view's own repository, and {@link ActorRepository}, count; and that the selected row's
         * actor is in the table, which is what the equality says of every row.
         *
         * @param actorInfo one of the rows selected in {@link ActorInfoRepository_SpringBootIT#__()}.
         */
        @MethodSource("actorInfos")
        @ParameterizedTest
        void __(final ActorInfo actorInfo) {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.getActorInfos()).isPositive().isEqualTo(counts.getActors());
            assertThat(counts.getActorInfos()).isEqualTo(repositoryInstance().count());
            assertThat(counts.getActors()).isEqualTo(actorRepository.count());
            assertThat(actorRepository.existsById(actorInfo.getActorId())).isTrue();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private ActorRepository actorRepository;
}
