package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmCategoryRepository.FilmCategoryCount;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategoryId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
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
     * Creates a new instance.
     */
    FilmCategoryRepository_DataJpaTest() {
        super(FilmCategoryRepository.class, FilmCategory.class, FilmCategoryId.class);
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
     * Tests {@link FilmCategoryRepository#selectFilmsWithMoreCategoriesThan(long, Pageable)}
     * ({@code SAKILA-FILM_CATEGORY-I-01}).
     */
    @Nested
    class SelectFilmsWithMoreCategoriesThan_Test {

        private static final Sort BY_FILM_ID = Sort.by("filmId");

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
         * the first alone, with its count, one in all, and that a threshold of zero selects the first two by film id,
         * two in all, which the count query says even when the page holds one.
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
                    .extracting(FilmCategoryCount::getFilmId, FilmCategoryCount::getCategories)
                    .containsExactly(tuple(two.getFilmId(), 2L));
            assertThat(aboveOne.getTotalElements()).isEqualTo(1L);
            final var aboveZero = repositoryInstance().selectFilmsWithMoreCategoriesThan(
                    0L, PageRequest.of(0, 1, BY_FILM_ID));
            assertThat(aboveZero.getContent())
                    .extracting(FilmCategoryCount::getFilmId, FilmCategoryCount::getCategories)
                    .containsExactly(tuple(two.getFilmId(), 2L));
            assertThat(aboveZero.getTotalElements()).isEqualTo(2L);
            assertThat(aboveZero.getTotalPages()).isEqualTo(2);
            final var next = repositoryInstance().selectFilmsWithMoreCategoriesThan(0L, aboveZero.nextPageable());
            assertThat(next.getContent())
                    .extracting(FilmCategoryCount::getFilmId, FilmCategoryCount::getCategories)
                    .containsExactly(tuple(one.getFilmId(), 1L));
            assertThat(next.hasNext()).isFalse();
        }
    }

    /**
     * Tests {@link FilmCategoryRepository#countFilmsWithoutCategory()} ({@code SAKILA-FILM_CATEGORY-I-02}).
     */
    @Nested
    class CountFilmsWithoutCategory_Test {

        /**
         * Asserts that, with no film, the count is zero.
         */
        @Test
        void _Zero_NoFilm() {
            assertThat(repositoryInstance().countFilmsWithoutCategory()).isZero();
        }

        /**
         * Saves a film of one category and two of none, and asserts that the two are counted: the films, not the
         * links, and only those with no link.
         */
        @Test
        void __() {
            fixtures().filmCategory(fixtures().film(), fixtures().category());
            fixtures().film();
            fixtures().film();
            assertThat(repositoryInstance().countFilmsWithoutCategory()).isEqualTo(2L);
        }
    }
}
