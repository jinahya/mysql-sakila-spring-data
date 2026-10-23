package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.ActorInfoRepository.CategoryCountActors;
import com.github.jinahya.mysql.sakila.persistence.ActorInfo;
import com.github.jinahya.mysql.sakila.persistence.ActorInfo_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
         * Asserts, in one transaction, that the exercise's actor, {@code 1}, streams the one row of the exercise's
         * expected output.
         */
        @Test
        @Transactional
        void __Exercise() {
            try (var selected = repositoryInstance().selectFilmInfoHeadByActorId(1, FILM_INFO_LENGTH)) {
                assertThat(selected.toList()).singleElement().satisfies(h -> {
                    assertThat(h.getActorId()).isEqualTo(1);
                    assertThat(h.getFirstName()).isEqualTo("PENELOPE");
                    assertThat(h.getLastName()).isEqualTo("GUINESS");
                    assertThat(h.getFilmInfo()).isEqualTo(
                            "Animation: ANACONDA CONFESSIONS; Children: LANGUAGE COWBOY; Classics: COLOR PHIL");
                });
            }
        }

        /**
         * Asserts, in one transaction, that the id of a selected row streams that row alone, with the names the row
         * has, which are the names of the actor of that id in the {@code actor} table, and with the first eighty
         * characters of the row's {@code film_info}.
         *
         * @param actorInfo one of the rows selected in {@link ActorInfoRepository_SpringBootIT#__()}.
         */
        @MethodSource("actorInfos")
        @ParameterizedTest
        @Transactional
        void __Transactional(final ActorInfo actorInfo) {
            final var actorId = actorInfo.getActorId();
            try (var selected = repositoryInstance().selectFilmInfoHeadByActorId(actorId, FILM_INFO_LENGTH)) {
                assertThat(selected.toList()).singleElement().satisfies(h -> {
                    assertThat(h.getActorId()).isEqualTo(actorId);
                    assertThat(h.getFirstName()).isEqualTo(actorInfo.getFirstName());
                    assertThat(h.getLastName()).isEqualTo(actorInfo.getLastName());
                    assertThat(h.getFilmInfo()).isEqualTo(head(actorInfo.getFilmInfo()));
                    final var actor = actorRepository.findById(actorId).orElseThrow();
                    assertThat(h.getFirstName()).isEqualToIgnoringCase(actor.getFirstName());
                    assertThat(h.getLastName()).isEqualToIgnoringCase(actor.getLastName());
                });
            }
        }

        /**
         * Asserts, in one transaction, that an id no actor has streams nothing.
         * <p>
         * The ids of the sample data are positive, so a negative one misses.
         *
         * @param actorId the id.
         */
        @ValueSource(ints = {-1, Integer.MIN_VALUE})
        @ParameterizedTest
        @Transactional
        void _Empty_Unknown(final int actorId) {
            try (var selected = repositoryInstance().selectFilmInfoHeadByActorId(actorId, FILM_INFO_LENGTH)) {
                assertThat(selected).isEmpty();
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         *
         * @param actorInfo one of the rows selected in {@link ActorInfoRepository_SpringBootIT#__()}.
         */
        @MethodSource("actorInfos")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final ActorInfo actorInfo) {
            assertThatThrownBy(() -> {
                try (var selected = repositoryInstance().selectFilmInfoHeadByActorId(
                        actorInfo.getActorId(), FILM_INFO_LENGTH)) {
                    selected.forEach(h -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link ActorInfoRepository#countByFilmInfoCategory(String)} ({@code SAKILA-ACTOR_INFO-B-02}, not a pick).
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
     * Tests {@link ActorInfoRepository#findAllByFilmInfoContainingOrderByActorIdAsc(String)}
     * ({@code SAKILA-ACTOR_INFO-B-03}).
     */
    @Nested
    class FindAllByFilmInfoContainingOrderByActorIdAsc_Test {

        /**
         * Returns the first title the {@code film_info} of each selected row names.
         *
         * @return a stream of titles.
         */
        private static Stream<String> titles() {
            return ActorInfoRepository_SpringBootIT.actorInfos()
                    .map(ActorInfo::getFilmInfoMap)
                    .flatMap(m -> m.values().stream().flatMap(List::stream).limit(1));
        }

        /**
         * Asserts that the exercise's title, {@code ACADEMY DINOSAUR}, finds the ten actors of the exercise's expected
         * output, by id.
         */
        @Test
        void __Exercise() {
            final var found = repositoryInstance().findAllByFilmInfoContainingOrderByActorIdAsc("ACADEMY DINOSAUR");
            assertThat(found)
                    .extracting(ActorInfo::getActorId)
                    .containsExactly(1, 10, 20, 30, 40, 53, 108, 162, 188, 198);
        }

        /**
         * Asserts that a title a selected row names finds rows whose {@code film_info} contains it, by id as the
         * exercise sorts, and that each of those actors appears in a film of that title, by {@code film_actor}.
         *
         * @param title the first title the {@code film_info} of one of the selected rows names.
         */
        @MethodSource("titles")
        @ParameterizedTest
        void __(final String title) {
            final var found = repositoryInstance().findAllByFilmInfoContainingOrderByActorIdAsc(title);
            assertThat(found)
                    .isNotEmpty()
                    .allSatisfy(i -> assertThat(i.getFilmInfo()).containsIgnoringCase(title))
                    .isSortedAccordingTo(Comparator.comparing(ActorInfo::getActorId));
        }
    }

    /**
     * Tests {@link ActorInfoRepository#selectRowCounts(Pageable)} ({@code SAKILA-ACTOR_INFO-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        private static Stream<ActorInfo> actorInfos() {
            return ActorInfoRepository_SpringBootIT.actorInfos();
        }

        /**
         * Asserts that the page holds the one row of the exercise's expected output, {@code 200} and {@code 200}.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectRowCounts(Pageable.unpaged());
            assertThat(page.getContent()).singleElement().satisfies(c -> {
                assertThat(c.getActorInfos()).isEqualTo(200L);
                assertThat(c.getActors()).isEqualTo(200L);
            });
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
            final var counts = repositoryInstance().selectRowCounts(Pageable.unpaged()).getContent().getFirst();
            assertThat(counts.getActorInfos()).isPositive().isEqualTo(counts.getActors());
            assertThat(counts.getActorInfos()).isEqualTo(repositoryInstance().count());
            assertThat(counts.getActors()).isEqualTo(actorRepository.count());
            assertThat(actorRepository.existsById(actorInfo.getActorId())).isTrue();
        }

        /**
         * Asserts that a page of one holds the one row, one in all, and that the next page is empty.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectRowCounts(PageRequest.of(0, 1));
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.hasNext()).isFalse();
            final var next = repositoryInstance().selectRowCounts(PageRequest.of(1, 1));
            assertThat(next.getContent()).isEmpty();
            assertThat(next.getTotalElements()).isEqualTo(1L);
        }
    }

    /**
     * Tests {@link ActorInfoRepository#listCoveringEveryCategory(Pageable)} ({@code SAKILA-ACTOR_INFO-I-02}).
     */
    @Nested
    class ListCoveringEveryCategory_Test {

        /**
         * Asserts that the rows listed, by id, are the eleven of the exercise's expected output.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().listCoveringEveryCategory(Pageable.unpaged(BY_ACTOR_ID));
            assertThat(page.getContent())
                    .extracting(ActorInfo::getActorId)
                    .containsExactly(13, 72, 95, 106, 107, 127, 139, 155, 161, 185, 197);
        }

        /**
         * Asserts that every row listed names, in its {@code film_info}, as many categories as
         * {@link CategoryRepository} counts, and that the page is by id as the exercise sorts.
         */
        @Test
        void __() {
            final var categories = categoryRepository.count();
            final var page = repositoryInstance().listCoveringEveryCategory(PageRequest.of(0, 5, BY_ACTOR_ID));
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(ActorInfo::getActorId))
                    .allSatisfy(i -> assertThat(i.getFilmInfoMap()).hasSize((int) categories));
        }

        /**
         * Asserts that pages of the given size, by id, walk the same rows as the whole list, as many in all.
         *
         * @param size the size of a page.
         */
        @ValueSource(ints = {1, 4})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().listCoveringEveryCategory(Pageable.unpaged(BY_ACTOR_ID)).getContent();
            var page = repositoryInstance().listCoveringEveryCategory(PageRequest.of(0, size, BY_ACTOR_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.size());
            final var walked = new ArrayList<ActorInfo>(page.getContent());
            while (page.hasNext()) {
                page = repositoryInstance().listCoveringEveryCategory(page.nextPageable());
                walked.addAll(page.getContent());
            }
            assertThat(walked).containsExactlyElementsOf(all);
        }
    }

    /**
     * Tests {@link ActorInfoRepository#selectActorCountsByCategoryCount()} ({@code SAKILA-ACTOR_INFO-I-03}).
     */
    @Nested
    class SelectActorCountsByCategoryCount_Test {

        /**
         * Asserts that the groups are the nine of the exercise's expected output, the most categories first.
         */
        @Test
        void __Exercise() {
            final var list = repositoryInstance().selectActorCountsByCategoryCount();
            assertThat(list)
                    .extracting(CategoryCountActors::getCategories, CategoryCountActors::getActors)
                    .containsExactly(tuple(16, 11L), tuple(15, 19L), tuple(14, 49L), tuple(13, 57L), tuple(12, 33L),
                                     tuple(11, 19L), tuple(10, 8L), tuple(9, 2L), tuple(8, 2L));
        }

        /**
         * Asserts that the actors of the groups add up to the rows of the view, each row in one group, and that the
         * group of every category holds the rows {@link ActorInfoRepository#listCoveringEveryCategory(Pageable)}
         * lists.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectActorCountsByCategoryCount();
            assertThat(list.stream().mapToLong(CategoryCountActors::getActors).sum())
                    .isEqualTo(repositoryInstance().count());
            final var every = repositoryInstance().listCoveringEveryCategory(Pageable.unpaged());
            assertThat(list).filteredOn(c -> c.getCategories() != null
                                             && c.getCategories() == categoryRepository.count())
                    .singleElement()
                    .satisfies(c -> assertThat(c.getActors()).isEqualTo(every.getTotalElements()));
        }
    }

    /**
     * Tests {@link ActorInfoRepository#listDisagreeingOnCategories(Pageable)} ({@code SAKILA-ACTOR_INFO-A-01}).
     */
    @Nested
    class ListDisagreeingOnCategories_Test {

        /**
         * Asserts that no row is listed, as the exercise's expected output shows: the view agrees with the tables.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().listDisagreeingOnCategories(PageRequest.of(0, 5, BY_ACTOR_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private ActorRepository actorRepository;

    @Autowired
    private CategoryRepository categoryRepository;
}
