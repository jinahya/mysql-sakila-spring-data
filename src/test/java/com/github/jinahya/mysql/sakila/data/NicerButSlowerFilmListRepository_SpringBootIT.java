package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmList;
import com.github.jinahya.mysql.sakila.persistence.NicerButSlowerFilmList;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link NicerButSlowerFilmListRepository} against the Sakila database.
 * <p>
 * The repository is not a Spring Data one, so this does not extend {@link _Repository_SpringBootIT}: it is a plain
 * {@code @SpringBootTest}, over the context of {@link ___Spring_TestContext}, which {@code @Import}s the
 * implementation. There is no {@code @DataJpaTest} for it either: the slice's H2 is generated from the entities, and
 * the view does not exist there. Each repository call runs in a transaction of its own, and nothing here writes.
 * <p>
 * The fixture is the first five rows of the view, by {@code FID}, selected once in {@link #__()}; each test tries
 * every one of them, or a few page sizes. The view is {@code film_list} with the actors' names capitalized, so what it
 * says is cross-checked against the same rows of {@code film_list}, through {@link FilmListRepository}.
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class NicerButSlowerFilmListRepository_SpringBootIT {

    private static List<NicerButSlowerFilmList> rows;

    /**
     * The sort of the exercises: by {@code FID}, which the implementation appends anyway.
     */
    private static final Sort BY_FID = Sort.by(Sort.Direction.ASC, NicerButSlowerFilmList.ATTRIBUTE_NAME_FID);

    /**
     * The same sort, by {@code FID}, for {@link FilmListRepository}.
     */
    private static final Sort FILM_LIST_BY_FID = Sort.by(Sort.Direction.ASC, FilmList.ATTRIBUTE_NAME_FID);

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

    private static Stream<NicerButSlowerFilmList> rows() {
        return rows.stream();
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
     * Tests {@link NicerButSlowerFilmListRepository#listByFid(Integer, Pageable)}
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-01}).
     */
    @Nested
    class ListByFid_Test {

        private static Stream<NicerButSlowerFilmList> rows() {
            return NicerButSlowerFilmListRepository_SpringBootIT.rows();
        }

        /**
         * Asserts that the {@code FID} of a selected row lists rows of that {@code FID} only, the selected row among
         * them, and compares them with the same rows of {@code film_list}, as the exercise does: as many, with the
         * same title, and the same actors, written with only the first letter of each name in upper case.
         *
         * @param row one of the rows selected in {@link NicerButSlowerFilmListRepository_SpringBootIT#__()}.
         */
        @MethodSource("rows")
        @ParameterizedTest
        void __(final NicerButSlowerFilmList row) {
            final var fid = row.getFid();
            final var page = repository.listByFid(fid, Pageable.unpaged(BY_FID));
            assertThat(page.getContent())
                    .isNotEmpty()
                    .allSatisfy(r -> assertThat(r.getFid()).isEqualTo(fid))
                    .anySatisfy(r -> {
                        assertThat(r).usingRecursiveComparison()
                                .ignoringFields(NicerButSlowerFilmList.ATTRIBUTE_NAME_ACTORS)
                                .isEqualTo(row);
                        assertThat(namesOf(r.getActors()))
                                .containsExactlyInAnyOrderElementsOf(namesOf(row.getActors()));
                    });
            final var filmList = filmListRepository.listByFid(fid, Pageable.unpaged(FILM_LIST_BY_FID));
            assertThat(filmList.getContent()).hasSameSizeAs(page.getContent());
            for (int i = 0; i < page.getNumberOfElements(); i++) {
                final var nicer = page.getContent().get(i);
                final var other = filmList.getContent().get(i);
                assertThat(nicer.getTitle()).isEqualTo(other.getTitle());
                assertThat(nicer.getCategory()).isEqualTo(other.getCategory());
                assertCapitalized(nicer.getActors(), other.getActors());
            }
        }

        /**
         * Asserts that pages of one, walked one after another, add up to the single unpaged page of the selected
         * row's {@code FID}, row by row, with the same total.
         *
         * @param row one of the rows selected in {@link NicerButSlowerFilmListRepository_SpringBootIT#__()}.
         */
        @MethodSource("rows")
        @ParameterizedTest
        void __Paged(final NicerButSlowerFilmList row) {
            final var fid = row.getFid();
            final var all = repository.listByFid(fid, Pageable.unpaged(BY_FID));
            assertThat(all.getContent()).isNotEmpty();
            final var walked = new ArrayList<NicerButSlowerFilmList>();
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
     * Tests {@link NicerButSlowerFilmListRepository#countDifferingFromFilmList()}
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-A-01}).
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

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private NicerButSlowerFilmListRepository repository;

    @Autowired
    private FilmListRepository filmListRepository;
}
