package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Category;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategoryId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link FilmCategoryRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the films, categories and links it needs,
 * through the fixtures, and the slice rolls them back when it ends, so a test starts over empty tables and can count
 * what it finds. Unlike the sample data, the H2 lets a test give a film two categories, so the exercise about them
 * is checked with rows here, and against none in the integration test.
 *
 * @see FilmCategoryRepository_SpringBootIT
 */
class FilmCategoryRepository_DataJpaTest
        extends _Repository_DataJpaTest<FilmCategoryRepository, FilmCategory, FilmCategoryId> {

    /**
     * A sort by the film's id, by its path from the query's identification variable, {@code f}.
     */
    private static final Sort BY_FILM_ID = Sort.by(Sort.Direction.ASC, "f.filmId");

    /**
     * Creates a new instance.
     */
    FilmCategoryRepository_DataJpaTest() {
        super(FilmCategoryRepository.class, FilmCategory.class, FilmCategoryId.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Saves a category whose name starts with the specified prefix, so that a test can order categories by name.
     * <p>
     * The rest of the name is random, and short enough for the {@code 25} characters of the column.
     *
     * @param prefix the prefix of the name.
     * @return the saved category, managed in the test's persistence context.
     */
    private Category saveCategory(final String prefix) {
        final var category = fixtures().category();
        category.setName(prefix + randomName().substring(0, 13));
        return category;
    }

    /**
     * Saves a film of the specified length, in a language of its own.
     *
     * @param length the length of the film, in minutes.
     * @return the saved film, managed in the test's persistence context.
     */
    private Film saveFilm(final int length) {
        final var film = fixtures().film();
        film.setLength(length);
        return film;
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link FilmCategoryRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Saves a film and a category, with no link, and asserts that finding the link of the two returns an empty
         * optional.
         */
        @Test
        void _Empty_Unknown() {
            final var film = fixtures().film();
            final var category = fixtures().category();
            final var id = FilmCategoryId.of(film.getFilmId(), category.getCategoryId());
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves a link of a film and a category, and asserts that finding it by its id returns an equal link, of
         * that film and that category.
         * <p>
         * The id of a link is derived from the two, so {@code save} merges rather than persists, and what comes back
         * may be a copy of what was passed; hence equal, not the same.
         */
        @Test
        void _NotEmpty_Known() {
            final var film = fixtures().film();
            final var category = fixtures().category();
            final var saved = fixtures().filmCategory(film, category);
            final var id = FilmCategoryId.of(film.getFilmId(), category.getCategoryId());
            final var found = repositoryInstance().findById(id);
            assertThat(found).hasValueSatisfying(v -> {
                assertThat(v).isEqualTo(saved);
                assertThat(v.getFilm()).isEqualTo(film);
                assertThat(v.getCategory()).isEqualTo(category);
            });
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmCategoryRepository#findAllByFilmIdBetweenOrderByFilmId(Integer, Integer)}
     * ({@code SAKILA_FILM_CATEGORY_B_02}).
     */
    @Nested
    class FindAllByFilmIdBetweenOrderByFilmId_Test {

        /**
         * Saves a film of a category, and asserts that a range of negative ids, which no film has, finds an empty
         * list.
         */
        @Test
        void _Empty_Unknown() {
            fixtures().filmCategory(fixtures().film(), fixtures().category());
            final var list = repositoryInstance().findAllByFilmIdBetweenOrderByFilmId(Integer.MIN_VALUE, -1);
            assertThat(list).isEmpty();
        }

        /**
         * Saves three films, links the second, the first and the third to a category each, in that order, and asserts
         * that the range of the first two ids finds their links only, the bounds included, by film id as the exercise
         * orders.
         * <p>
         * The films are saved in order, so their ids ascend in that order; the links are saved in another, so that
         * the order is the query's, not the insertion's.
         */
        @Test
        void __() {
            final var film1 = fixtures().film();
            final var film2 = fixtures().film();
            final var film3 = fixtures().film();
            final var category2 = fixtures().category();
            final var category1 = fixtures().category();
            fixtures().filmCategory(film2, category2);
            fixtures().filmCategory(film1, category1);
            fixtures().filmCategory(film3, fixtures().category());
            final var list = repositoryInstance().findAllByFilmIdBetweenOrderByFilmId(
                    film1.getFilmId(), film2.getFilmId());
            assertThat(list)
                    .extracting(fc -> fc.getFilm().getFilmId(), fc -> fc.getCategory().getCategoryId())
                    .containsExactly(
                            tuple(film1.getFilmId(), category1.getCategoryId()),
                            tuple(film2.getFilmId(), category2.getCategoryId())
                    );
        }

        /**
         * Asserts that a {@code null} bound is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullBound() {
            assertThatThrownBy(() -> repositoryInstance().findAllByFilmIdBetweenOrderByFilmId(null, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repositoryInstance().findAllByFilmIdBetweenOrderByFilmId(1, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectFilmsWithMoreCategoriesThan(long, Pageable)}
     * ({@code SAKILA_FILM_CATEGORY_I_01}).
     */
    @Nested
    class SelectFilmsWithMoreCategoriesThan_Test {

        private static final Sort BY_FILM_ID = Sort.by("f.filmId");

        /**
         * Saves a film of one category, and asserts that a threshold of one selects an empty page, with no elements
         * in all, as it does on the sample data.
         */
        @Test
        void _Empty_NoneAbove() {
            fixtures().filmCategory(fixtures().film(), fixtures().category());
            final var page = repositoryInstance().selectFilmsWithMoreCategoriesThan(
                    1L, PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves a film of two categories, one of one and one of none, and asserts that a threshold of one selects
         * the first film itself alone, with its count, one in all, and that a threshold of zero selects the first two
         * by film id, two in all, which the count query says even when the page holds one.
         */
        @Test
        void __() {
            final var two = fixtures().film();
            final var one = fixtures().film();
            fixtures().film();
            fixtures().filmCategory(two, fixtures().category());
            fixtures().filmCategory(two, fixtures().category());
            fixtures().filmCategory(one, fixtures().category());
            final var aboveOne = repositoryInstance().selectFilmsWithMoreCategoriesThan(
                    1L, PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(aboveOne.getContent())
                    .extracting(column("film", Film.class), column("categories", Long.class))
                    .containsExactly(tuple(two, 2L));
            assertThat(aboveOne.getTotalElements()).isEqualTo(1L);
            final var aboveZero = repositoryInstance().selectFilmsWithMoreCategoriesThan(
                    0L, PageRequest.of(0, 1, BY_FILM_ID));
            assertThat(aboveZero.getContent())
                    .extracting(column("film", Film.class), column("categories", Long.class))
                    .containsExactly(tuple(two, 2L));
            assertThat(aboveZero.getTotalElements()).isEqualTo(2L);
            assertThat(aboveZero.getTotalPages()).isEqualTo(2);
            final var next = repositoryInstance().selectFilmsWithMoreCategoriesThan(0L, aboveZero.nextPageable());
            assertThat(next.getContent())
                    .extracting(column("film", Film.class), column("categories", Long.class))
                    .containsExactly(tuple(one, 1L));
            assertThat(next.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectFilmsWithMoreCategoriesThan(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectFilmsWithoutCategory(Pageable)} ({@code SAKILA_FILM_CATEGORY_I_02}).
     */
    @Nested
    class SelectFilmsWithoutCategory_Test {

        /**
         * Asserts that, with no film, the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoFilm() {
            final var page = repositoryInstance().selectFilmsWithoutCategory(PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves a film of one category and two of none, and asserts that the two are selected, the films, not the
         * links, and only those with no link: two in all, which is the count the exercise asks for, and pages of one
         * by id split them one and one.
         */
        @Test
        void __() {
            fixtures().filmCategory(fixtures().film(), fixtures().category());
            final var none1 = fixtures().film();
            final var none2 = fixtures().film();
            final var all = repositoryInstance().selectFilmsWithoutCategory(Pageable.unpaged(BY_FILM_ID));
            assertThat(all.getContent()).containsExactly(none1, none2);
            assertThat(all.getTotalElements()).isEqualTo(2L);
            final var page0 = repositoryInstance().selectFilmsWithoutCategory(PageRequest.of(0, 1, BY_FILM_ID));
            assertThat(page0.getContent()).containsExactly(none1);
            assertThat(page0.getTotalElements()).isEqualTo(2L);
            final var page1 = repositoryInstance().selectFilmsWithoutCategory(page0.nextPageable());
            assertThat(page1.getContent()).containsExactly(none2);
            assertThat(page1.hasNext()).isFalse();
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectFilmsWithoutCategory(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectLongestFilms(Pageable)} ({@code SAKILA_FILM_CATEGORY_A_01}).
     */
    @Nested
    class SelectLongestFilms_Test {

        private static final Sort BY_NAME = Sort.by("c.name");

        /**
         * Saves a category with no film, and asserts that the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoLink() {
            fixtures().category();
            final var page = repositoryInstance().selectLongestFilms(PageRequest.of(0, 5, BY_NAME));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves a category {@code a-...} of films of 100, 120 and 120 minutes, a category {@code b-...} of a film of
         * 90, and a category of no film, and asserts that the page holds the first two categories, by name as the
         * exercise orders, each with its longest film, the first of the two of 120 by id, two in all; and that a page
         * of one holds the first alone, still two in all.
         */
        @Test
        void __() {
            final var a = saveCategory("a-");
            final var b = saveCategory("b-");
            saveCategory("c-");
            final var a100 = saveFilm(100);
            final var a120 = saveFilm(120);
            final var a120too = saveFilm(120);
            final var b90 = saveFilm(90);
            fixtures().filmCategory(a100, a);
            fixtures().filmCategory(a120too, a);
            fixtures().filmCategory(a120, a);
            fixtures().filmCategory(b90, b);
            final var page = repositoryInstance().selectLongestFilms(PageRequest.of(0, 5, BY_NAME));
            assertThat(page.getContent())
                    .extracting(column("category", Category.class), column("film", Film.class),
                                column("film", Film.class, Film::getLength))
                    .containsExactly(
                            tuple(a, a120, 120),
                            tuple(b, b90, 90)
                    );
            assertThat(page.getTotalElements()).isEqualTo(2L);
            final var first = repositoryInstance().selectLongestFilms(PageRequest.of(0, 1, BY_NAME));
            assertThat(first.getContent())
                    .extracting(column("category", Category.class))
                    .containsExactly(a);
            assertThat(first.getTotalElements()).isEqualTo(2L);
            assertThat(first.getTotalPages()).isEqualTo(2);
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectLongestFilms(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#selectCategoryPairsWithSameFilmCount(Pageable)}
     * ({@code SAKILA_FILM_CATEGORY_A_02}).
     */
    @Nested
    class SelectCategoryPairsWithSameFilmCount_Test {

        private static final Sort BY_FILMS_DESC_THEN_CATEGORIES =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("c1.name", "c2.name"));

        /**
         * Saves a category of two films and a category of one, and asserts that the page is empty, with no elements
         * in all.
         */
        @Test
        void _Empty_NoSameCount() {
            final var a = saveCategory("a-");
            final var b = saveCategory("b-");
            fixtures().filmCategory(fixtures().film(), a);
            fixtures().filmCategory(fixtures().film(), a);
            fixtures().filmCategory(fixtures().film(), b);
            final var page = repositoryInstance().selectCategoryPairsWithSameFilmCount(
                    PageRequest.of(0, 5, BY_FILMS_DESC_THEN_CATEGORIES));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves categories {@code a-...} and {@code b-...} of two films each, {@code c-...} and {@code e-...} of one
         * each, and {@code d-...} of none, and asserts that the page holds the two pairs of the same count, the
         * greater count first as the exercise sorts, each pair once with the smaller name first, two in all; that a
         * category of no film is paired with none; and that a page of one holds the first pair alone, still two in
         * all.
         */
        @Test
        void __() {
            final var a = saveCategory("a-");
            final var b = saveCategory("b-");
            final var c = saveCategory("c-");
            saveCategory("d-");
            final var e = saveCategory("e-");
            for (final var category : List.of(a, a, b, b, c, e)) {
                fixtures().filmCategory(fixtures().film(), category);
            }
            final var page = repositoryInstance().selectCategoryPairsWithSameFilmCount(
                    PageRequest.of(0, 5, BY_FILMS_DESC_THEN_CATEGORIES));
            assertThat(page.getContent())
                    .extracting(column("category1", Category.class), column("category2", Category.class),
                                column("films", Long.class))
                    .containsExactly(
                            tuple(a, b, 2L),
                            tuple(c, e, 1L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(2L);
            final var first = repositoryInstance().selectCategoryPairsWithSameFilmCount(
                    PageRequest.of(0, 1, BY_FILMS_DESC_THEN_CATEGORIES));
            assertThat(first.getContent())
                    .extracting(column("category1", Category.class), column("category2", Category.class))
                    .containsExactly(tuple(a, b));
            assertThat(first.getTotalElements()).isEqualTo(2L);
            assertThat(first.getTotalPages()).isEqualTo(2);
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectCategoryPairsWithSameFilmCount(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
