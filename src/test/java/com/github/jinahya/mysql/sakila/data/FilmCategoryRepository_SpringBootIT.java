package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmCategoryRepository.FilmCategoryCount;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategoryId;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
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
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmCategoryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few links of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * the counts of the other repositories. The sample data gives every film exactly one category, so the exercise about
 * films with more than one is checked to select none here, and with rows in the slice test. The base's own tests,
 * such as its {@code findById} round trips, run here too.
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
         * Asserts that the film of a selected link is among the films with more than no category, and that the
         * total of them is the number of distinct films among the links.
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
     * Tests {@link FilmCategoryRepository#countFilmsWithoutCategory()} ({@code SAKILA-FILM_CATEGORY-I-02}).
     */
    @Nested
    class CountFilmsWithoutCategory_Test {

        /**
         * Asserts that the count is the number of all films, through {@link FilmRepository}, less the number of films
         * with a category, which {@link FilmCategoryRepository#selectFilmsWithMoreCategoriesThan(long, Pageable)}
         * groups above zero; on the sample data, that is zero.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countFilmsWithoutCategory();
            final var all = filmRepository.count();
            final var withCategories = repositoryInstance()
                    .selectFilmsWithMoreCategoriesThan(0L, Pageable.unpaged())
                    .getTotalElements();
            assertThat(count)
                    .isNotNegative()
                    .isEqualTo(all - withCategories);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
