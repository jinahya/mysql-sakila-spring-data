package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmCategoryRepository.CategoryLongestFilm;
import com.github.jinahya.mysql.sakila.data.FilmCategoryRepository.CategoryPairFilmCount;
import com.github.jinahya.mysql.sakila.data.FilmCategoryRepository.FilmCategoryCount;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategoryId;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import com.github.jinahya.mysql.sakila.persistence.Film_;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmCategoryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few links of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against the exercise's
 * output, or against them, or against the counts of the other repositories. The sample data gives every film exactly
 * one category, so the exercise about films with more than one is checked to select none here, and with rows in the
 * slice test. The base's own tests, such as its {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see FilmCategoryRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilmCategoryRepository_SpringBootIT
        extends _Repository_SpringBootIT<FilmCategoryRepository, FilmCategory, FilmCategoryId> {

    private static List<FilmCategory> entities;

    /**
     * A sort by the film's id, then by the category's; by the attributes' names, since the metamodel's
     * {@code FilmCategory_.filmId} is filled in only once the context is up, after this class is loaded.
     */
    private static final Sort BY_FILM_ID_THEN_CATEGORY_ID =
            Sort.by(Sort.Direction.ASC, FilmCategory_.FILM_ID, FilmCategory_.CATEGORY_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    FilmCategoryRepository_SpringBootIT() {
        super(FilmCategoryRepository.class, FilmCategory.class, FilmCategoryId.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five links, by the film's id and then the category's, as the fixture for the parameterized
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
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_FILM_ID_THEN_CATEGORY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<FilmCategory> filmCategories() {
        return entities.stream();
    }

    /**
     * Counts the links of the film of the specified id, through a {@link Specification}.
     *
     * @param filmId the id of the film.
     * @return the number of categories of the film.
     */
    private long countByFilmId(final Integer filmId) {
        final Specification<FilmCategory> specification = (r, q, b) -> b.equal(r.get(FilmCategory_.filmId), filmId);
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
     * Tests {@link FilmCategoryRepository#findAllByFilmIdBetweenOrderByFilmId(Integer, Integer)}
     * ({@code SAKILA-FILM_CATEGORY-B-02}).
     */
    @Nested
    class FindAllByFilmIdBetweenOrderByFilmId_Test {

        private static Stream<FilmCategory> filmCategories() {
            return FilmCategoryRepository_SpringBootIT.filmCategories();
        }

        /**
         * Asserts that the films {@code 1} to {@code 10} find the exercise's output, by film id.
         */
        @Test
        void __Expected() {
            final var list = repositoryInstance().findAllByFilmIdBetweenOrderByFilmId(1, 10);
            assertThat(list)
                    .extracting(fc -> fc.getFilm().getFilmId(), fc -> fc.getCategory().getCategoryId())
                    .containsExactly(
                            tuple(1, 6),
                            tuple(2, 11),
                            tuple(3, 6),
                            tuple(4, 11),
                            tuple(5, 8),
                            tuple(6, 9),
                            tuple(7, 5),
                            tuple(8, 11),
                            tuple(9, 11),
                            tuple(10, 15)
                    );
        }

        /**
         * Asserts that the range of a selected link's film id alone finds that link, and links of that film only.
         *
         * @param filmCategory one of the links selected in {@link FilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("filmCategories")
        @ParameterizedTest
        void __(final FilmCategory filmCategory) {
            final var filmId = filmCategory.getFilm().getFilmId();
            final var list = repositoryInstance().findAllByFilmIdBetweenOrderByFilmId(filmId, filmId);
            assertThat(list)
                    .contains(filmCategory)
                    .allSatisfy(fc -> assertThat(fc.getFilm().getFilmId()).isEqualTo(filmId));
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectFilmsWithMoreCategoriesThan(long, Pageable)}
     * ({@code SAKILA-FILM_CATEGORY-I-01}).
     */
    @Nested
    class SelectFilmsWithMoreCategoriesThan_Test {

        private static final Sort BY_FILM_ID = Sort.by("filmId");

        private static Stream<FilmCategory> filmCategories() {
            return FilmCategoryRepository_SpringBootIT.filmCategories();
        }

        /**
         * Asserts that the exercise's threshold, one, selects nothing, as the exercise's output says, since every film
         * of the sample data has exactly one category.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectFilmsWithMoreCategoriesThan(
                    1L, PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that every film selected above the given threshold has more categories than it, that the page is
         * ordered by film id, and that each count is the number of links of the film.
         * <p>
         * Above one, the exercise's threshold, the sample data selects none, so the page is empty and the assertions
         * on its content are vacuous; above zero, every film with a category is selected.
         *
         * @param categories the threshold.
         */
        @ValueSource(longs = {0L, 1L})
        @ParameterizedTest
        void __(final long categories) {
            final var page = repositoryInstance().selectFilmsWithMoreCategoriesThan(
                    categories, PageRequest.of(0, 5, BY_FILM_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .allSatisfy(c -> assertThat(c.getCategories()).isGreaterThan(categories))
                    .isSortedAccordingTo(Comparator.comparing(FilmCategoryCount::getFilmId))
                    .allSatisfy(c -> {
                        assertThat(c.getCategories()).as("categories of film %d", c.getFilmId())
                                .isEqualTo(countByFilmId(c.getFilmId()));
                    });
        }

        /**
         * Asserts that the film of a selected link is among the films with more than no category.
         *
         * @param filmCategory one of the links selected in {@link FilmCategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("filmCategories")
        @ParameterizedTest
        void __ContainsFilmOfFixture(final FilmCategory filmCategory) {
            final var all = repositoryInstance().selectFilmsWithMoreCategoriesThan(0L, Pageable.unpaged(BY_FILM_ID));
            assertThat(all.getContent())
                    .extracting(FilmCategoryCount::getFilmId)
                    .contains(filmCategory.getFilm().getFilmId());
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectFilmsWithoutCategory(Pageable)} ({@code SAKILA-FILM_CATEGORY-I-02}).
     */
    @Nested
    class SelectFilmsWithoutCategory_Test {

        private static final Sort BY_FILM_ID = Sort.by(Film_.FILM_ID);

        /**
         * Asserts that the page is empty, with no elements in all, which is the exercise's output, {@code 0}, and is
         * the number of all films, through {@link FilmRepository}, less the number of films with a category, which
         * {@link FilmCategoryRepository#selectFilmsWithMoreCategoriesThan(long, Pageable)} groups above zero.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectFilmsWithoutCategory(PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            final var withCategories = repositoryInstance()
                    .selectFilmsWithMoreCategoriesThan(0L, Pageable.unpaged())
                    .getTotalElements();
            assertThat(page.getTotalElements()).isEqualTo(filmRepository.count() - withCategories);
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectLongestFilms(Pageable)} ({@code SAKILA-FILM_CATEGORY-A-01}).
     */
    @Nested
    class SelectLongestFilms_Test {

        private static final Sort BY_NAME = Sort.by("name");

        /**
         * Asserts that all the categories, by name as the exercise orders, are the exercise's output.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectLongestFilms(Pageable.unpaged(BY_NAME));
            assertThat(page.getContent())
                    .extracting(CategoryLongestFilm::getName, CategoryLongestFilm::getFilmId,
                                CategoryLongestFilm::getTitle, CategoryLongestFilm::getLength)
                    .containsExactly(
                            tuple("Action", 212, "DARN FORRESTER", 185),
                            tuple("Animation", 349, "GANGS PRIDE", 185),
                            tuple("Children", 344, "FURY MURDER", 178),
                            tuple("Classics", 180, "CONSPIRACY SPIRIT", 184),
                            tuple("Comedy", 182, "CONTROL ANTHEM", 185),
                            tuple("Documentary", 973, "WIFE TURN", 183),
                            tuple("Drama", 473, "JACKET FRISCO", 181),
                            tuple("Family", 499, "KING EVOLUTION", 184),
                            tuple("Foreign", 198, "CRYSTAL BREAKING", 184),
                            tuple("Games", 141, "CHICAGO NORTH", 185),
                            tuple("Horror", 24, "ANALYZE HOOSIERS", 181),
                            tuple("Music", 426, "HOME PITY", 185),
                            tuple("New", 340, "FRONTIER CABIN", 183),
                            tuple("Sci-Fi", 817, "SOLDIERS EVOLUTION", 185),
                            tuple("Sports", 813, "SMOOCHY CONTROL", 184),
                            tuple("Travel", 609, "MUSCLE BRIGHT", 185)
                    );
            assertThat(page.getTotalElements()).isEqualTo(16L);
        }

        /**
         * Asserts that a page of the given size, by name, is the start of the unpaged list, with the same total.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().selectLongestFilms(Pageable.unpaged(BY_NAME));
            final var page = repositoryInstance().selectLongestFilms(PageRequest.of(0, size, BY_NAME));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            assertThat(page.getContent())
                    .extracting(CategoryLongestFilm::getName)
                    .containsExactlyElementsOf(all.getContent().stream().limit(size)
                                                       .map(CategoryLongestFilm::getName).toList());
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectCategoryPairsWithSameFilmCount(Pageable)}
     * ({@code SAKILA-FILM_CATEGORY-A-02}).
     */
    @Nested
    class SelectCategoryPairsWithSameFilmCount_Test {

        private static final Sort BY_FILMS_DESC_THEN_CATEGORIES =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("category1", "category2"));

        /**
         * Asserts that the pairs, the greater count first and then by names as the exercise sorts, are the exercise's
         * output, two in all.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectCategoryPairsWithSameFilmCount(
                    PageRequest.of(0, 5, BY_FILMS_DESC_THEN_CATEGORIES));
            assertThat(page.getContent())
                    .extracting(CategoryPairFilmCount::getCategory1, CategoryPairFilmCount::getCategory2,
                                CategoryPairFilmCount::getFilms)
                    .containsExactly(
                            tuple("Games", "Sci-Fi", 61L),
                            tuple("Classics", "Travel", 57L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        /**
         * Asserts that a page of one holds the first pair, still two in all, and the next page the second.
         */
        @Test
        void __Paged() {
            final var page0 = repositoryInstance().selectCategoryPairsWithSameFilmCount(
                    PageRequest.of(0, 1, BY_FILMS_DESC_THEN_CATEGORIES));
            assertThat(page0.getContent())
                    .extracting(CategoryPairFilmCount::getCategory1)
                    .containsExactly("Games");
            assertThat(page0.getTotalElements()).isEqualTo(2L);
            final var page1 = repositoryInstance().selectCategoryPairsWithSameFilmCount(page0.nextPageable());
            assertThat(page1.getContent())
                    .extracting(CategoryPairFilmCount::getCategory1)
                    .containsExactly("Classics");
            assertThat(page1.hasNext()).isFalse();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
