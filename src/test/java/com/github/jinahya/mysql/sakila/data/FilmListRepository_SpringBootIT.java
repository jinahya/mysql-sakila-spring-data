package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Category_;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmActor_;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import com.github.jinahya.mysql.sakila.persistence.FilmList;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmListRepository} against the Sakila database.
 * <p>
 * The repository is not a Spring Data one, so this does not extend {@link _Repository_SpringBootIT}: it is a plain
 * {@code @SpringBootTest}, over the context of {@link ___Spring_TestContext}, which {@code @Import}s the
 * implementation. There is no {@code @DataJpaTest} for it either: the slice's H2 is generated from the entities, and
 * the view does not exist there. Each repository call runs in a transaction of its own, and nothing here writes.
 * <p>
 * The fixture is the first five rows of the view, by {@code FID}, selected once in {@link #__()}; each test tries
 * every one of them, or a few page sizes. What the view says is cross-checked through the repositories of the tables it
 * is built from: {@link FilmRepository} for the films and their categories, and {@link FilmActorRepository} for the
 * actors.
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class FilmListRepository_SpringBootIT {

    private static List<FilmList> rows;

    /**
     * The sort of the exercises: by {@code FID}, which the implementation appends anyway.
     */
    private static final Sort BY_FID = Sort.by(Sort.Direction.ASC, FilmList.ATTRIBUTE_NAME_FID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rows of the view, by {@code FID}, as the fixture for the parameterized tests.
     * <p>
     * Runs once per class, which {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the
     * autowired repository is already there; the result goes into a static field so that the static method sources of
     * the nested classes can reach it. An empty view aborts the tests of this class rather than failing them: it is the
     * fixture that is missing, not the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        rows = Collections.unmodifiableList(repository.listAll(PageRequest.of(0, 5, BY_FID)).getContent());
        rows.forEach(r -> log.debug("row: {}", r));
        assumeThat(rows).isNotEmpty();
    }

    private static Stream<FilmList> rows() {
        return rows.stream();
    }

    private static Stream<String> categories() {
        return rows.stream().map(FilmList::getCategory).distinct();
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

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmListRepository#countByCategory(String)} ({@code SAKILA-FILM_LIST-B-01}).
     */
    @Nested
    class CountByCategory_Test {

        private static Stream<String> categories() {
            return FilmListRepository_SpringBootIT.categories();
        }

        /**
         * Asserts that the category of a selected row counts a positive number of rows, which is the number of
         * {@code film_category} links to a category of that name, counted through {@link FilmRepository} with a join;
         * the view has one row for each such link.
         *
         * @param category the category of one of the rows selected in {@link FilmListRepository_SpringBootIT#__()}.
         */
        @MethodSource("categories")
        @ParameterizedTest
        void __(final String category) {
            final var count = repository.countByCategory(category);
            log.debug("{} films: {}", category, count);
            final Specification<Film> specification = (r, q, b) -> b.equal(
                    r.join(Film_.filmCategories).get(FilmCategory_.category).get(Category_.name), category);
            assertThat(count)
                    .isPositive()
                    .isEqualTo(filmRepository.count(specification));
        }

        /**
         * Asserts that a category no film has counts zero.
         */
        @Test
        void _Zero_Unknown() {
            assertThat(repository.countByCategory("no such category, surely")).isZero();
        }
    }

    /**
     * Tests {@link FilmListRepository#listByFid(Integer, Pageable)} ({@code SAKILA-FILM_LIST-B-02}).
     */
    @Nested
    class ListByFid_Test {

        private static Stream<FilmList> rows() {
            return FilmListRepository_SpringBootIT.rows();
        }

        /**
         * Asserts that the {@code FID} of a selected row lists rows of that {@code FID} only, the selected row among
         * them, and that each says what the film says: its title and its rating, through {@link FilmRepository}, and
         * as many actors as the film has {@code film_actor} links, counting the names in the comma-separated string.
         *
         * @param row one of the rows selected in {@link FilmListRepository_SpringBootIT#__()}.
         */
        @MethodSource("rows")
        @ParameterizedTest
        void __(final FilmList row) {
            final var fid = row.getFid();
            final var page = repository.listByFid(fid, Pageable.unpaged(BY_FID));
            final var film = filmRepository.findById(fid).orElseThrow();
            final var actors = countActorsOf(film);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .allSatisfy(r -> {
                        assertThat(r.getFid()).isEqualTo(fid);
                        assertThat(r.getTitle()).isEqualTo(film.getTitle());
                        assertThat(r.getRating()).isEqualTo(film.getRating());
                        assertThat(namesOf(r.getActors())).hasSize((int) actors);
                    })
                    .anySatisfy(r -> {
                        assertThat(r).usingRecursiveComparison()
                                .ignoringFields(FilmList.ATTRIBUTE_NAME_ACTORS)
                                .isEqualTo(row);
                        assertThat(namesOf(r.getActors()))
                                .containsExactlyInAnyOrderElementsOf(namesOf(row.getActors()));
                    });
        }

        /**
         * Asserts that pages of one, walked one after another, add up to the single unpaged page of the selected
         * row's {@code FID}, row by row, with the same total.
         *
         * @param row one of the rows selected in {@link FilmListRepository_SpringBootIT#__()}.
         */
        @MethodSource("rows")
        @ParameterizedTest
        void __Paged(final FilmList row) {
            final var fid = row.getFid();
            final var all = repository.listByFid(fid, Pageable.unpaged(BY_FID));
            assertThat(all.getContent()).isNotEmpty();
            final var walked = new ArrayList<FilmList>();
            var page = repository.listByFid(fid, PageRequest.of(0, 1, BY_FID));
            while (true) {
                assertPage(page);
                assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
                walked.addAll(page.getContent());
                if (!page.hasNext()) {
                    break;
                }
                page = repository.listByFid(fid, page.nextPageable());
            }
            assertSameRows(walked, all.getContent());
        }

        /**
         * Asserts that an {@code FID} no film has lists nothing, with a total of zero.
         */
        @Test
        void _Empty_Unknown() {
            final var page = repository.listByFid(0, PageRequest.of(0, 1, BY_FID));
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link FilmListRepository#countAll()} ({@code SAKILA-FILM_LIST-I-01}).
     */
    @Nested
    class CountAll_Test {

        /**
         * Asserts that the count is the total of the unpaged listing, and is the number of films which have a
         * category, counted through {@link FilmRepository}, as the exercise checks; the two are the same since no film
         * of the sample data has more than one category.
         */
        @Test
        void __() {
            final var count = repository.countAll();
            log.debug("film_list: {}", count);
            assertThat(count)
                    .isPositive()
                    .isEqualTo(repository.listAll(Pageable.unpaged()).getTotalElements());
            final Specification<Film> specification = (r, q, b) -> b.isNotEmpty(r.get(Film_.filmCategories));
            assertThat(count).isEqualTo(filmRepository.count(specification));
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
