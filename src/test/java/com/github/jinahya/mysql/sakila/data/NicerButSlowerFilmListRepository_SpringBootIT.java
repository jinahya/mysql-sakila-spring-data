package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.NicerButSlowerFilmList;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link NicerButSlowerFilmListRepository} against the Sakila database.
 * <p>
 * The repository is not a Spring Data one, so this does not extend {@link _Repository_SpringBootIT}: it is a plain
 * {@code @SpringBootTest}, over the context of {@link ___Spring_TestContext}, which {@code @Import}s the
 * implementation. There is no {@code @DataJpaTest} for it either: the slice's H2 is generated from the entities, the
 * view does not exist there, and every exercise method is native-only, running MySQL's own SQL. Each repository call
 * runs in a transaction of its own, and nothing here writes.
 * <p>
 * The exercise methods run the exercises' SQL as it is, so each is asserted against the output upstream records for it,
 * in {@code src/test/sql/nicer_but_slower_film_list_exercises_0N_<level>.sql}, in the order the exercise asks for. The
 * view is {@code film_list} with the actors' names capitalized, so what it says is cross-checked, where it is cheap,
 * against {@code film_list}, through {@link FilmListRepository}.
 * <p>
 * Two reads of the same row are compared ignoring the order of the names in {@code actors}: both views build that
 * string with a {@code GROUP_CONCAT} that has no {@code ORDER BY}, so MySQL may list the names in a different order
 * from one statement to the next, and from one view to the other; see {@link #namesOf(String)}.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see NicerButSlowerFilmListRepository
 * @see NicerButSlowerFilmListRepositoryImpl
 */
@SpringBootTest
@Slf4j
class NicerButSlowerFilmListRepository_SpringBootIT {

    /**
     * A sort by {@code FID}, which the implementation appends anyway.
     */
    private static final Sort BY_FID = Sort.by(Sort.Direction.ASC, NicerButSlowerFilmList.ATTRIBUTE_NAME_FID);

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
    private static void assertSameRows(final List<NicerButSlowerFilmList> actual,
                                       final List<NicerButSlowerFilmList> expected) {
        assertThat(actual)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(NicerButSlowerFilmList.ATTRIBUTE_NAME_ACTORS)
                .containsExactlyElementsOf(expected);
        for (int i = 0; i < actual.size(); i++) {
            assertThat(namesOf(actual.get(i).getActors()))
                    .containsExactlyInAnyOrderElementsOf(namesOf(expected.get(i).getActors()));
        }
    }

    /**
     * Asserts that the specified actors, as this view writes them, are the specified actors of {@code film_list}: the
     * same names, ignoring case and order, each written with its first letter in upper case and the rest in lower
     * case.
     *
     * @param nicer    the actors of a row of this view; may be {@code null}, for a film with no actor.
     * @param filmList the actors of the same row of {@code film_list}; may be {@code null}, likewise.
     */
    private static void assertCapitalized(final String nicer, final String filmList) {
        assertThat(namesOf(nicer)).map(String::toLowerCase)
                .containsExactlyInAnyOrderElementsOf(namesOf(filmList).stream().map(String::toLowerCase).toList());
        for (final var name : namesOf(nicer)) {
            for (final var word : name.split(" ")) {
                assertThat(word).as("word of %s", name).isNotEmpty();
                assertThat(word.substring(0, 1)).isUpperCase();
                assertThat(word.substring(1)).isLowerCase();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link NicerButSlowerFilmListRepository#listAll(Pageable)}.
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
                    .isSortedAccordingTo(Comparator.comparing(NicerButSlowerFilmList::getFid));
        }

        /**
         * Asserts that a page sorted by price, descending, and then by length is ordered so, and then by {@code FID},
         * which the implementation appends.
         * <p>
         * A film with no length is sorted first by MySQL, as {@code NULL} is the smallest; none of the sample data has
         * none, but the comparator puts it first too.
         */
        @Test
        void __SortedByPriceDescThenLength() {
            final var sort = Sort.by(Sort.Direction.DESC, NicerButSlowerFilmList.ATTRIBUTE_NAME_PRICE)
                    .and(Sort.by(NicerButSlowerFilmList.ATTRIBUTE_NAME_LENGTH));
            final var page = repository.listAll(PageRequest.of(0, 20, sort));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(NicerButSlowerFilmList::getPrice).reversed()
                                                 .thenComparing(NicerButSlowerFilmList::getLength,
                                                                Comparator.nullsFirst(Comparator.naturalOrder()))
                                                 .thenComparing(NicerButSlowerFilmList::getFid));
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
            final var walked = new ArrayList<NicerButSlowerFilmList>();
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

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link NicerButSlowerFilmListRepository#listActorsOfFilmOne()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_B_01}).
     */
    @Nested
    class ListActorsOfFilmOne_Test {

        /**
         * Asserts that the film {@code 1} has the one row the exercise shows, {@code ACADEMY DINOSAUR} with its ten
         * actors as this view writes them, in whatever order, and compares it with the same row of {@code film_list},
         * as the exercise does: the same title, and the same actors, written with only the first letter of each name in
         * upper case.
         */
        @Test
        void __() {
            final var list = repository.listActorsOfFilmOne();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list).singleElement().satisfies(r -> {
                assertThat(r.getFid()).isEqualTo(1);
                assertThat(r.getTitle()).isEqualTo("ACADEMY DINOSAUR");
                assertThat(namesOf(r.getActors())).containsExactlyInAnyOrder(
                        "Penelope Guiness", "Christian Gable", "Lucille Tracy", "Sandra Peck", "Johnny Cage",
                        "Mena Temple", "Warren Nolte", "Oprah Kilmer", "Rock Dukakis", "Mary Keitel");
            });
            final var filmList = filmListRepository.listActorsOfFilmOne();
            assertThat(filmList).hasSameSizeAs(list);
            for (int i = 0; i < list.size(); i++) {
                assertThat(list.get(i).getTitle()).isEqualTo(filmList.get(i).getTitle());
                assertCapitalized(list.get(i).getActors(), filmList.get(i).getActors());
            }
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#countFilmsOfPenelopeGuiness()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_B_02}).
     */
    @Nested
    class CountFilmsOfPenelopeGuiness_Test {

        /**
         * Asserts that the count is the exercise's {@code 19}, and is the number of rows of the view whose actors
         * name her, counted over the unpaged listing.
         */
        @Test
        void __() {
            final var count = repository.countFilmsOfPenelopeGuiness();
            log.debug("films: {}", count);
            assertThat(count)
                    .isEqualTo(19L)
                    .isEqualTo(repository.listAll(Pageable.unpaged(BY_FID)).stream()
                                       .filter(r -> namesOf(r.getActors()).contains("Penelope Guiness"))
                                       .count());
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#listFilmsWithNoActor()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_B_03}).
     */
    @Nested
    class ListFilmsWithNoActor_Test {

        /**
         * Asserts that the films are the exercise's three, with their categories, by {@code FID}, the same as
         * {@code film_list}'s.
         */
        @Test
        void __() {
            final var list = repository.listFilmsWithNoActor();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(NicerButSlowerFilmList::getFid, NicerButSlowerFilmList::getTitle,
                                NicerButSlowerFilmList::getCategory)
                    .containsExactly(
                            tuple(257, "DRUMLINE CYCLONE", "Travel"),
                            tuple(323, "FLIGHT LIES", "Sports"),
                            tuple(803, "SLACKER LIAISONS", "Drama"));
            assertThat(list).extracting(NicerButSlowerFilmList::getActors).containsOnlyNulls();
            assertThat(list)
                    .extracting(NicerButSlowerFilmList::getFid, NicerButSlowerFilmList::getTitle,
                                NicerButSlowerFilmList::getDescription, NicerButSlowerFilmList::getCategory,
                                NicerButSlowerFilmList::getPrice, NicerButSlowerFilmList::getLength,
                                NicerButSlowerFilmList::getRating)
                    .containsExactlyElementsOf(
                            filmListRepository.listFilmsWithNoActor().stream()
                                    .map(r -> tuple(r.getFid(), r.getTitle(), r.getDescription(), r.getCategory(),
                                                    r.getPrice(), r.getLength(), r.getRating()))
                                    .toList());
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#listFiveLongestCategoriesOnAverage()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_I_01}).
     */
    @Nested
    class ListFiveLongestCategoriesOnAverage_Test {

        /**
         * Asserts that the rows are the exercise's five, with the number of their films and their average lengths.
         */
        @Test
        void __() {
            final var list = repository.listFiveLongestCategoriesOnAverage();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(NicerButSlowerFilmListRepository.CategoryAverageLength::category,
                                NicerButSlowerFilmListRepository.CategoryAverageLength::films,
                                NicerButSlowerFilmListRepository.CategoryAverageLength::averageLength)
                    .containsExactly(
                            tuple("Sports", 74L, new BigDecimal("128.20")),
                            tuple("Games", 61L, new BigDecimal("127.84")),
                            tuple("Foreign", 73L, new BigDecimal("121.70")),
                            tuple("Drama", 62L, new BigDecimal("120.84")),
                            tuple("Comedy", 58L, new BigDecimal("115.83")));
        }

        /**
         * Asserts that the rows are sorted by the average length, descending, as the exercise asks, and that there are
         * no more than five.
         */
        @Test
        void __Sorted() {
            assertThat(repository.listFiveLongestCategoriesOnAverage())
                    .hasSizeLessThanOrEqualTo(5)
                    .isSortedAccordingTo(Comparator.comparing(
                            NicerButSlowerFilmListRepository.CategoryAverageLength::averageLength).reversed());
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#countFilmsByLengthBand()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_I_02}).
     */
    @Nested
    class CountFilmsByLengthBand_Test {

        /**
         * Asserts that the rows are the exercise's three bands, by their shortest length, and that their films add up
         * to every row of the view.
         */
        @Test
        void __() {
            final var list = repository.countFilmsByLengthBand();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(NicerButSlowerFilmListRepository.LengthBand::band,
                                NicerButSlowerFilmListRepository.LengthBand::films,
                                NicerButSlowerFilmListRepository.LengthBand::shortest,
                                NicerButSlowerFilmListRepository.LengthBand::longest)
                    .containsExactly(
                            tuple("short", 96L, 46, 59),
                            tuple("medium", 447L, 60, 120),
                            tuple("long", 457L, 121, 185));
            assertThat(list)
                    .extracting(NicerButSlowerFilmListRepository.LengthBand::shortest)
                    .isSorted();
            assertThat(list.stream().mapToLong(NicerButSlowerFilmListRepository.LengthBand::films).sum())
                    .isEqualTo(repository.listAll(Pageable.unpaged(BY_FID)).getTotalElements());
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#countDifferingFromFilmList()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_A_01}).
     */
    @Nested
    class CountDifferingFromFilmList_Test {

        /**
         * Asserts that no row differs from {@code film_list} in any column but the actors, as the exercise says: the
         * two views differ only in how the actors are written, which is a property of their definitions, not of the
         * data.
         */
        @Test
        void _Zero_() {
            final var differing = repository.countDifferingFromFilmList();
            log.debug("differing: {}", differing);
            assertThat(differing).isZero();
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#listLongestByCategory()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_A_02}).
     */
    @Nested
    class ListLongestByCategory_Test {

        /**
         * Asserts that the rows are the exercise's sixteen, one per category, with its longest length and the number of
         * its films of that length.
         */
        @Test
        void __() {
            final var list = repository.listLongestByCategory();
            list.forEach(r -> log.debug("row: {}", r));
            assertThat(list)
                    .extracting(NicerButSlowerFilmListRepository.CategoryLongest::category,
                                NicerButSlowerFilmListRepository.CategoryLongest::longest,
                                NicerButSlowerFilmListRepository.CategoryLongest::films)
                    .containsExactly(
                            tuple("Action", 185, 2L),
                            tuple("Animation", 185, 2L),
                            tuple("Children", 178, 2L),
                            tuple("Classics", 184, 1L),
                            tuple("Comedy", 185, 1L),
                            tuple("Documentary", 183, 2L),
                            tuple("Drama", 181, 1L),
                            tuple("Family", 184, 1L),
                            tuple("Foreign", 184, 2L),
                            tuple("Games", 185, 1L),
                            tuple("Horror", 181, 2L),
                            tuple("Music", 185, 1L),
                            tuple("New", 183, 1L),
                            tuple("Sci-Fi", 185, 1L),
                            tuple("Sports", 184, 1L),
                            tuple("Travel", 185, 2L));
        }

        /**
         * Asserts that the rows are sorted by the category, as the exercise asks; the names are compared ignoring case,
         * as the database's collation compares them.
         */
        @Test
        void __Sorted() {
            assertThat(repository.listLongestByCategory())
                    .extracting(NicerButSlowerFilmListRepository.CategoryLongest::category)
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        }
    }

    /**
     * Tests {@link NicerButSlowerFilmListRepository#countMismatchedActors()}
     * ({@code SAKILA_NICER_BUT_SLOWER_FILM_LIST_A_03}).
     */
    @Nested
    class CountMismatchedActors_Test {

        /**
         * Asserts that no film is miscounted, as the exercise says, and that {@code film_list} agrees.
         */
        @Test
        void _Zero_() {
            final var mismatched = repository.countMismatchedActors();
            log.debug("mismatched: {}", mismatched);
            assertThat(mismatched).isZero();
            assertThat(filmListRepository.listFilmsMiscountingActors()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private NicerButSlowerFilmListRepository repository;

    @Autowired
    private FilmListRepository filmListRepository;
}
