package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Category_;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import com.github.jinahya.mysql.sakila.persistence.FilmList;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link FilmListRepository} against the Sakila database.
 * <p>
 * The repository is not a Spring Data one, so this does not extend {@link _Repository_SpringBootIT}: it is a plain
 * {@code @SpringBootTest}, over the context of {@link ___Spring_TestContext}, which {@code @Import}s the
 * implementation. There is no {@code @DataJpaTest} for it either: the slice's H2 is generated from the entities, the
 * view does not exist there, and every exercise method is native-only, running MySQL's own SQL. Each repository call
 * runs in a transaction of its own, and nothing here writes.
 * <p>
 * The exercise methods run the exercises' SQL as it is, so each is asserted against the output upstream records for it,
 * in {@code src/test/sql/film_list_exercises_0N_<level>.sql}, in the order the exercise asks for, and, where it is
 * cheap, cross-checked through the repositories of the tables the view is built from: {@link FilmRepository} for the
 * films and their categories, and {@link FilmActorRepository} for the actors.
 * <p>
 * Two reads of the same row are compared ignoring the order of the names in {@code actors}: the view builds that
 * string with a {@code GROUP_CONCAT} that has no {@code ORDER BY}, so MySQL may list the names in a different order
 * from one statement to the next, as it does between a {@code LIMIT}ed and an unlimited select; see
 * {@link #namesOf(String)}.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see FilmListRepository
 * @see FilmListRepositoryImpl
 */
@SpringBootTest
@Slf4j
class FilmListRepository_SpringBootIT {

    /**
     * A sort by {@code FID}, which the implementation appends anyway.
     */
    private static final Sort BY_FID = Sort.by(Sort.Direction.ASC, FilmList.ATTRIBUTE_NAME_FID);

    // -----------------------------------------------------------------------------------------------------------------

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

    /**
     * Splits the specified {@code actors} of a row into the names it lists, to compare them regardless of their order.
     *
     * @param actors the comma-separated names; may be {@code null}, for a film with no actor.
     * @return the names; empty if {@code actors} is {@code null}.
     */
    private static List<String> namesOf(final String actors) {
        return actors == null ? List.of() : List.of(actors.split(", "));
    }

    /**
     * Asserts that the specified rows are the same rows of the view, in the same order: equal in every column but
     * {@code actors}, and listing the same names there, in whatever order.
     *
     * @param actual   the rows read.
     * @param expected the rows expected.
     */
    private static void assertSameRows(final List<FilmList> actual, final List<FilmList> expected) {
        assertThat(actual)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(FilmList.ATTRIBUTE_NAME_ACTORS)
                .containsExactlyElementsOf(expected);
        for (int i = 0; i < actual.size(); i++) {
            assertThat(namesOf(actual.get(i).getActors()))
                    .containsExactlyInAnyOrderElementsOf(namesOf(expected.get(i).getActors()));
        }
    }

    /**
     * Counts the {@code film_actor} links of the specified film, through {@link FilmActorRepository}.
     *
     * @param film the film.
     * @return the number of actors of {@code film}.
     */
    private long countActorsOf(final Film film) {
        final Specification<FilmActor> specification = (r, q, b) -> b.equal(r.get(FilmActor_.film), film);
        return filmActorRepository.count(specification);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link FilmListRepository#listAll(Pageable)}.
     */
    @Nested
    class ListAll_Test {

        /**
         * Asserts that the first page of the given size, by {@code FID}, is consistent with itself, and ordered so.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __(final int size) {
            final var page = repository.listAll(PageRequest.of(0, size, BY_FID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(FilmList::getFid));
        }

        /**
         * Asserts that a page sorted by title, descending, is ordered so, and then by {@code FID}, which the
         * implementation appends.
         * <p>
         * The titles are compared ignoring case, as the database's collation compares them.
         */
        @Test
        void __SortedByTitleDesc() {
            final var sort = Sort.by(Sort.Direction.DESC, FilmList.ATTRIBUTE_NAME_TITLE);
            final var page = repository.listAll(PageRequest.of(0, 20, sort));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(FilmList::getTitle, String.CASE_INSENSITIVE_ORDER)
                                                 .reversed()
                                                 .thenComparing(FilmList::getFid));
        }

        /**
         * Asserts that the pages of the given size, walked one after another, add up to the single unpaged page, row
         * by row, and that every page carries the same total.
         *
         * @param size the size of the pages.
         */
        @ValueSource(ints = {100, 333})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repository.listAll(Pageable.unpaged(BY_FID));
            assertThat(all.getTotalElements()).isEqualTo(all.getNumberOfElements());
            final var walked = new ArrayList<FilmList>();
            var page = repository.listAll(PageRequest.of(0, size, BY_FID));
            while (true) {
                assertPage(page);
                assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
                walked.addAll(page.getContent());
                if (!page.hasNext()) {
                    break;
                }
                page = repository.listAll(page.nextPageable());
            }
            assertSameRows(walked, all.getContent());
        }

        /**
         * Asserts that a sort by a property the view has no column for is rejected.
         * <p>
         * The implementation throws {@link IllegalArgumentException}, which {@code @Repository} has Spring translate
         * into {@link InvalidDataAccessApiUsageException} on the way out.
         */
        @Test
        void _InvalidDataAccessApiUsageException_UnknownSortProperty() {
            final var sort = Sort.by(Film.ATTRIBUTE_NAME_FILM_ID);
            assertThatThrownBy(() -> repository.listAll(PageRequest.of(0, 1, sort)))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link FilmListRepository#countAll()}.
     */
    @Nested
    class CountAll_Test {

        /**
         * Asserts that the count is the total of the unpaged listing.
         */
        @Test
        void __() {
            final var count = repository.countAll();
            log.debug("film_list: {}", count);
            assertThat(count)
                    .isPositive()
                    .isEqualTo(repository.listAll(Pageable.unpaged()).getTotalElements());
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmListRepository#countActionFilms()} ({@code SAKILA_FILM_LIST_B_01}).
     */
    @Nested
    class CountActionFilms_Test {

        /**
         * Asserts that the count is the exercise's {@code 64}, and is the number of {@code film_category} links to the
         * {@code Action} category, counted through {@link FilmRepository} with a join; the view has one row for each
         * such link.
         */
        @Test
        void __() {
            final var count = repository.countActionFilms();
            log.debug("Action films: {}", count);
            final Specification<Film> specification = (r, q, b) -> b.equal(
                    r.join(Film_.filmCategories).get(FilmCategory_.category).get(Category_.name), "Action");
            assertThat(count)
                    .isEqualTo(64L)
                    .isEqualTo(filmRepository.count(specification));
        }
    }

    /**
     * Tests {@link FilmListRepository#listActorsOfFilmOne()} ({@code SAKILA_FILM_LIST_B_02}).
     */
    @Nested
    class ListActorsOfFilmOne_Test {

        /**
         * Asserts that the film {@code 1} has the one row the exercise shows, {@code ACADEMY DINOSAUR} with its ten
         * actors, in whatever order, and as many as the film has {@code film_actor} links, counted through
         * {@link FilmActorRepository}.
         */
        @Test
        void __() {
            final var list = repository.listActorsOfFilmOne();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list).singleElement().satisfies(r -> {
                assertThat(r.getFid()).isEqualTo(1);
                assertThat(r.getTitle()).isEqualTo("ACADEMY DINOSAUR");
                assertThat(namesOf(r.getActors())).containsExactlyInAnyOrder(
                        "PENELOPE GUINESS", "CHRISTIAN GABLE", "LUCILLE TRACY", "SANDRA PECK", "JOHNNY CAGE",
                        "MENA TEMPLE", "WARREN NOLTE", "OPRAH KILMER", "ROCK DUKAKIS", "MARY KEITEL");
                final var film = filmRepository.findById(r.getFid()).orElseThrow();
                assertThat(namesOf(r.getActors())).hasSize((int) countActorsOf(film));
            });
        }
    }

    /**
     * Tests {@link FilmListRepository#listChildrenFilmsRatedG()} ({@code SAKILA_FILM_LIST_B_03}).
     */
    @Nested
    class ListChildrenFilmsRatedG_Test {

        /**
         * Asserts that the films are the exercise's ten, with their lengths, and that every row is of the
         * {@code Children} category and rated {@code G}.
         */
        @Test
        void __() {
            final var list = repository.listChildrenFilmsRatedG();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(FilmList::getFid, FilmList::getTitle, FilmList::getLength)
                    .containsExactly(
                            tuple(238, "DOCTOR GRAIL", 57),
                            tuple(280, "EMPIRE MALKOVICH", 177),
                            tuple(304, "FARGO GANDHI", 130),
                            tuple(354, "GHOST GROUNDHOG", 85),
                            tuple(373, "GRADUATE LORD", 156),
                            tuple(409, "HEARTBREAKERS BRIGHT", 59),
                            tuple(626, "NOON PAPI", 57),
                            tuple(853, "STRANGER STRANGERS", 139),
                            tuple(873, "SWEETHEARTS SUSPECTS", 108),
                            tuple(959, "WARLOCK WEREWOLF", 83));
            assertThat(list)
                    .extracting(FilmList::getCategory, FilmList::getRating)
                    .containsOnly(tuple("Children", Film.Rating.G));
        }

        /**
         * Asserts that the films are sorted by title, and then by {@code FID}, as the exercise asks; the titles are
         * compared ignoring case, as the database's collation compares them.
         */
        @Test
        void __Sorted() {
            assertThat(repository.listChildrenFilmsRatedG())
                    .isSortedAccordingTo(Comparator.comparing(FilmList::getTitle, String.CASE_INSENSITIVE_ORDER)
                                                 .thenComparing(FilmList::getFid));
        }
    }

    /**
     * Tests {@link FilmListRepository#listFilmsWithNoActor()} ({@code SAKILA_FILM_LIST_B_04}).
     */
    @Nested
    class ListFilmsWithNoActor_Test {

        /**
         * Asserts that the films are the exercise's three, with their categories, by {@code FID}, and that none of them
         * has a {@code film_actor} link, counted through {@link FilmActorRepository}.
         */
        @Test
        void __() {
            final var list = repository.listFilmsWithNoActor();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(FilmList::getFid, FilmList::getTitle, FilmList::getCategory)
                    .containsExactly(
                            tuple(257, "DRUMLINE CYCLONE", "Travel"),
                            tuple(323, "FLIGHT LIES", "Sports"),
                            tuple(803, "SLACKER LIAISONS", "Drama"));
            assertThat(list).extracting(FilmList::getActors).containsOnlyNulls();
            for (final var r : list) {
                final var film = filmRepository.findById(r.getFid()).orElseThrow();
                assertThat(countActorsOf(film)).isZero();
            }
        }
    }

    /**
     * Tests {@link FilmListRepository#countFilmListAndFilmsWithACategory()} ({@code SAKILA_FILM_LIST_I_01}).
     */
    @Nested
    class CountFilmListAndFilmsWithACategory_Test {

        /**
         * Asserts that both counts are the exercise's {@code 1000}: the first is {@link FilmListRepository#countAll()},
         * and the second the number of films which have a category, counted through {@link FilmRepository}.
         */
        @Test
        void __() {
            final var counts = repository.countFilmListAndFilmsWithACategory();
            log.debug("counts: {}", counts);
            assertThat(counts.filmList()).isEqualTo(1000L).isEqualTo(repository.countAll());
            final Specification<Film> specification = (r, q, b) -> b.isNotEmpty(r.get(Film_.filmCategories));
            assertThat(counts.filmsWithACategory())
                    .isEqualTo(1000L)
                    .isEqualTo(filmRepository.count(specification));
        }
    }

    /**
     * Tests {@link FilmListRepository#countFilmsByRatingAndPrice()} ({@code SAKILA_FILM_LIST_I_02}).
     */
    @Nested
    class CountFilmsByRatingAndPrice_Test {

        /**
         * Asserts that the rows are the exercise's five, one per rating, in the order of MySQL's {@code ENUM}, which is
         * the order of the {@link Film.Rating} constants too, and that their counts add up to every row of the view.
         */
        @Test
        void __() {
            final var list = repository.countFilmsByRatingAndPrice();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(FilmListRepository.RatingPrices::rating, FilmListRepository.RatingPrices::price099,
                                FilmListRepository.RatingPrices::price299, FilmListRepository.RatingPrices::price499)
                    .containsExactly(
                            tuple("G", 64L, 59L, 55L),
                            tuple("PG", 62L, 64L, 68L),
                            tuple("PG-13", 72L, 74L, 77L),
                            tuple("R", 70L, 60L, 65L),
                            tuple("NC-17", 73L, 66L, 71L));
            assertThat(list)
                    .extracting(r -> Film.Rating.valueOfColumnValue(r.rating()))
                    .isSorted();
            assertThat(list.stream().mapToLong(r -> r.price099() + r.price299() + r.price499()).sum())
                    .isEqualTo(repository.countAll());
        }
    }

    /**
     * Tests {@link FilmListRepository#listFilmsMiscountingActors()} ({@code SAKILA_FILM_LIST_I_03}).
     */
    @Nested
    class ListFilmsMiscountingActors_Test {

        /**
         * Asserts that no film is miscounted, as the exercise says.
         */
        @Test
        void __() {
            assertThat(repository.listFilmsMiscountingActors()).isEmpty();
        }
    }

    /**
     * Tests {@link FilmListRepository#countLongerThanAverageByCategory()} ({@code SAKILA_FILM_LIST_A_01}).
     */
    @Nested
    class CountLongerThanAverageByCategory_Test {

        /**
         * Asserts that the rows are the exercise's sixteen, one per category.
         */
        @Test
        void __() {
            final var list = repository.countLongerThanAverageByCategory();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(FilmListRepository.CategoryLongerThanAverage::category,
                                FilmListRepository.CategoryLongerThanAverage::longerThanAverage)
                    .containsExactly(
                            tuple("Sports", 42L),
                            tuple("Foreign", 37L),
                            tuple("Family", 36L),
                            tuple("New", 34L),
                            tuple("Animation", 32L),
                            tuple("Documentary", 31L),
                            tuple("Drama", 31L),
                            tuple("Sci-Fi", 31L),
                            tuple("Action", 30L),
                            tuple("Games", 30L),
                            tuple("Travel", 30L),
                            tuple("Children", 28L),
                            tuple("Comedy", 28L),
                            tuple("Horror", 28L),
                            tuple("Classics", 27L),
                            tuple("Music", 25L));
        }

        /**
         * Asserts that the rows are sorted by the count, descending, and then by the category, as the exercise asks;
         * the names are compared ignoring case, as the database's collation compares them.
         */
        @Test
        void __Sorted() {
            assertThat(repository.countLongerThanAverageByCategory())
                    .isSortedAccordingTo(
                            Comparator.comparingLong(FilmListRepository.CategoryLongerThanAverage::longerThanAverage)
                                    .reversed()
                                    .thenComparing(FilmListRepository.CategoryLongerThanAverage::category,
                                                   String.CASE_INSENSITIVE_ORDER));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmListRepository repository;

    @Autowired
    private FilmRepository filmRepository;

    @Autowired
    private FilmActorRepository filmActorRepository;
}
