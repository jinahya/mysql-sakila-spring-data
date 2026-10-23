package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.CategoryRepository.CategoryAverageLength;
import com.github.jinahya.mysql.sakila.data.CategoryRepository.CategoryFilmCount;
import com.github.jinahya.mysql.sakila.data.CategoryRepository.CategoryMostRentedFilm;
import com.github.jinahya.mysql.sakila.data.CategoryRepository.CategoryRunningFilmCount;
import com.github.jinahya.mysql.sakila.persistence.Category;
import com.github.jinahya.mysql.sakila.persistence.Category_;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
 * Tests {@link CategoryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few categories of
 * the sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or
 * against the expected output upstream's {@code category_exercises_*.sql} shows. The base's own tests, such as its
 * {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see CategoryRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryRepository_SpringBootIT
        extends _Repository_SpringBootIT<CategoryRepository, Category, Integer> {

    private static List<Category> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Category_.categoryId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_CATEGORY_ID = Sort.by(Sort.Direction.ASC, Category_.CATEGORY_ID);

    /**
     * The sixteen names of the categories of the sample data, by name.
     */
    private static final List<String> NAMES = List.of(
            "Action", "Animation", "Children", "Classics", "Comedy", "Documentary", "Drama", "Family", "Foreign",
            "Games", "Horror", "Music", "New", "Sci-Fi", "Sports", "Travel");

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    CategoryRepository_SpringBootIT() {
        super(CategoryRepository.class, Category.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five categories, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method; the result goes into a static field so
     * that the static method sources of the nested classes can reach it. The categories are loaded outside any
     * transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_CATEGORY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Category> categories() {
        return entities.stream();
    }

    /**
     * Counts the {@code film_category} links of the specified category, through {@link FilmCategoryRepository}.
     * <p>
     * The category's own {@code filmCategories} is not public upstream, so the links are counted by a
     * {@link Specification}.
     *
     * @param category the category.
     * @return the number of films of {@code category}.
     */
    private long filmsOf(final Category category) {
        final Specification<FilmCategory> specification =
                (r, q, b) -> b.equal(r.get(FilmCategory_.category), category);
        return filmCategoryRepository.count(specification);
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
     * Tests {@link CategoryRepository#findAllByOrderByNameAsc()} ({@code SAKILA-CATEGORY-B-01}).
     */
    @Nested
    class FindAllByOrderByNameAsc_Test {

        private static Stream<Category> categories() {
            return CategoryRepository_SpringBootIT.categories();
        }

        /**
         * Asserts that the categories found are the sixteen of the exercise's expected output, by name.
         */
        @Test
        void __Exercise() {
            final var found = repositoryInstance().findAllByOrderByNameAsc();
            assertThat(found).extracting(Category::getName).containsExactlyElementsOf(NAMES);
            assertThat(found).first().extracting(Category::getCategoryId).isEqualTo(1);
        }

        /**
         * Asserts that a selected category is found.
         *
         * @param category one of the categories selected in {@link CategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("categories")
        @ParameterizedTest
        void __(final Category category) {
            assertThat(repositoryInstance().findAllByOrderByNameAsc()).contains(category);
        }
    }

    /**
     * Tests {@link CategoryRepository#findAllByNameBetweenOrderByNameAsc(String, String)}
     * ({@code SAKILA-CATEGORY-B-02}).
     */
    @Nested
    class FindAllByNameBetweenOrderByNameAsc_Test {

        private static Stream<Category> categories() {
            return CategoryRepository_SpringBootIT.categories();
        }

        /**
         * Asserts, in one transaction, that the exercise's range, {@code D} to {@code H}, streams the five categories
         * of the exercise's expected output, by name.
         */
        @Test
        @Transactional
        void __Exercise() {
            try (var found = repositoryInstance().findAllByNameBetweenOrderByNameAsc("D", "H")) {
                assertThat(found.toList())
                        .extracting(Category::getCategoryId, Category::getName)
                        .containsExactly(
                                tuple(6, "Documentary"),
                                tuple(7, "Drama"),
                                tuple(8, "Family"),
                                tuple(9, "Foreign"),
                                tuple(10, "Games")
                        );
            }
        }

        /**
         * Asserts, in one transaction, that the range of a selected category's name to itself streams that category.
         *
         * @param category one of the categories selected in {@link CategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("categories")
        @ParameterizedTest
        @Transactional
        void __Transactional(final Category category) {
            final var name = category.getName();
            try (var found = repositoryInstance().findAllByNameBetweenOrderByNameAsc(name, name)) {
                assertThat(found.toList()).containsExactly(category);
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         *
         * @param category one of the categories selected in {@link CategoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("categories")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final Category category) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByNameBetweenOrderByNameAsc(
                        category.getName(), category.getName())) {
                    found.forEach(c -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link CategoryRepository#selectFilmCounts(Pageable)} ({@code SAKILA-CATEGORY-I-01}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_NAME =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("name"));

        /**
         * Asserts that the first page of five, the most films first, holds the five categories of the exercise's
         * expected output, of sixteen in all.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, 5, BY_FILMS_DESC_THEN_NAME));
            assertThat(page.getContent())
                    .extracting(CategoryFilmCount::getName, CategoryFilmCount::getFilms)
                    .containsExactly(
                            tuple("Sports", 74L),
                            tuple("Foreign", 73L),
                            tuple("Family", 69L),
                            tuple("Documentary", 68L),
                            tuple("Animation", 66L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(16L);
        }

        /**
         * Asserts that a page of the given size is ordered as the exercise sorts, and that each count is the number of
         * the category's {@code film_category} links.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 16})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, size, BY_FILMS_DESC_THEN_NAME));
            assertPage(page);
            assertThat(page.getContent())
                    .hasSize(size)
                    .isSortedAccordingTo(Comparator.comparingLong(CategoryFilmCount::getFilms).reversed()
                                                 .thenComparing(CategoryFilmCount::getName))
                    .allSatisfy(c -> {
                        final var category = repositoryInstance().findAll(
                                (r, q, b) -> b.equal(r.get(Category_.name), c.getName())).getFirst();
                        assertThat(filmsOf(category)).isEqualTo(c.getFilms());
                    });
        }
    }

    /**
     * Tests {@link CategoryRepository#selectAverageLengths(Pageable)} ({@code SAKILA-CATEGORY-I-02}).
     */
    @Nested
    class SelectAverageLengths_Test {

        private static final Sort BY_AVERAGE_LENGTH_DESC = Sort.by(Sort.Direction.DESC, "averageLength");

        /**
         * Asserts that the first page of one, the longest average first, holds {@code Sports}, with {@code 128.20}, as
         * the exercise's expected output shows, of sixteen in all.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectAverageLengths(PageRequest.of(0, 1, BY_AVERAGE_LENGTH_DESC));
            assertThat(page.getContent())
                    .extracting(CategoryAverageLength::getName, CategoryAverageLength::getAverageLength)
                    .containsExactly(tuple("Sports", 128.20d));
            assertThat(page.getTotalElements()).isEqualTo(16L);
        }

        /**
         * Asserts that pages of the given size, the longest average first, walk every category once, in descending
         * order of the average.
         *
         * @param size the size of a page.
         */
        @ValueSource(ints = {3, 7})
        @ParameterizedTest
        void __Paged(final int size) {
            final var walked = new ArrayList<CategoryAverageLength>();
            var page = repositoryInstance().selectAverageLengths(
                    PageRequest.of(0, size, BY_AVERAGE_LENGTH_DESC.and(Sort.by("name"))));
            walked.addAll(page.getContent());
            while (page.hasNext()) {
                page = repositoryInstance().selectAverageLengths(page.nextPageable());
                walked.addAll(page.getContent());
            }
            assertThat(walked)
                    .hasSize(NAMES.size())
                    .isSortedAccordingTo(Comparator.comparingDouble(CategoryAverageLength::getAverageLength)
                                                 .reversed())
                    .extracting(CategoryAverageLength::getName)
                    .containsExactlyInAnyOrderElementsOf(NAMES);
        }
    }

    /**
     * Tests {@link CategoryRepository#selectRunningFilmCounts(Pageable)} ({@code SAKILA-CATEGORY-A-01}).
     */
    @Nested
    class SelectRunningFilmCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_NAME =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("name"));

        /**
         * Asserts that the rows, the most films first and then by name, are the sixteen of the exercise's expected
         * output.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectRunningFilmCounts(PageRequest.of(0, 16, BY_FILMS_DESC_THEN_NAME));
            assertThat(page.getContent())
                    .extracting(CategoryRunningFilmCount::getName, CategoryRunningFilmCount::getFilms,
                                CategoryRunningFilmCount::getRunningFilms, CategoryRunningFilmCount::getRunningPercent)
                    .containsExactly(
                            tuple("Sports", 74L, 74L, 7.4d),
                            tuple("Foreign", 73L, 147L, 14.7d),
                            tuple("Family", 69L, 216L, 21.6d),
                            tuple("Documentary", 68L, 284L, 28.4d),
                            tuple("Animation", 66L, 350L, 35.0d),
                            tuple("Action", 64L, 414L, 41.4d),
                            tuple("New", 63L, 477L, 47.7d),
                            tuple("Drama", 62L, 539L, 53.9d),
                            tuple("Games", 61L, 600L, 60.0d),
                            tuple("Sci-Fi", 61L, 661L, 66.1d),
                            tuple("Children", 60L, 721L, 72.1d),
                            tuple("Comedy", 58L, 779L, 77.9d),
                            tuple("Classics", 57L, 836L, 83.6d),
                            tuple("Travel", 57L, 893L, 89.3d),
                            tuple("Horror", 56L, 949L, 94.9d),
                            tuple("Music", 51L, 1000L, 100.0d)
                    );
            assertThat(page.getTotalElements()).isEqualTo(16L);
        }

        /**
         * Asserts that the running total of each row is the one before it plus the row's own number of films, and that
         * a page of the given size, the next pages with it, carries on from where the last left.
         *
         * @param size the size of a page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __Paged(final int size) {
            var page = repositoryInstance().selectRunningFilmCounts(PageRequest.of(0, size, BY_FILMS_DESC_THEN_NAME));
            long running = 0L;
            while (true) {
                for (final var row : page.getContent()) {
                    running += row.getFilms();
                    assertThat(row.getRunningFilms()).as("running films of %s", row.getName()).isEqualTo(running);
                }
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().selectRunningFilmCounts(page.nextPageable());
            }
            assertThat(running).isEqualTo(filmCategoryRepository.count());
        }
    }

    /**
     * Tests {@link CategoryRepository#selectMostRentedFilms(Pageable)} ({@code SAKILA-CATEGORY-A-02}).
     */
    @Nested
    class SelectMostRentedFilms_Test {

        /**
         * Asserts that the rows, by name, are the sixteen of the exercise's expected output.
         */
        @Test
        void __Exercise() {
            final var page = repositoryInstance().selectMostRentedFilms(PageRequest.of(0, 16, Sort.by("name")));
            assertThat(page.getContent())
                    .extracting(CategoryMostRentedFilm::getName, CategoryMostRentedFilm::getTitle,
                                CategoryMostRentedFilm::getRentals)
                    .containsExactly(
                            tuple("Action", "RUGRATS SHAKESPEARE", 30L),
                            tuple("Animation", "JUGGLER HARDLY", 32L),
                            tuple("Children", "ROBBERS JOON", 31L),
                            tuple("Classics", "TIMBERLAND SKY", 31L),
                            tuple("Comedy", "ZORRO ARK", 31L),
                            tuple("Documentary", "WIFE TURN", 31L),
                            tuple("Drama", "HOBBIT ALIEN", 31L),
                            tuple("Family", "APACHE DIVINE", 31L),
                            tuple("Foreign", "ROCKETEER MOTHER", 33L),
                            tuple("Games", "FORWARD TEMPLE", 32L),
                            tuple("Horror", "PULP BEVERLY", 30L),
                            tuple("Music", "SCALAWAG DUCK", 32L),
                            tuple("New", "RIDGEMONT SUBMARINE", 32L),
                            tuple("Sci-Fi", "GOODFELLAS SALUTE", 31L),
                            tuple("Sports", "GLEAMING JAWBREAKER", 29L),
                            tuple("Travel", "BUCKET BROTHERHOOD", 34L)
                    );
            assertThat(page.getTotalElements()).isEqualTo(16L);
        }

        /**
         * Asserts that pages of five by name hold one row per category, five, five, five and one, sixteen in all.
         */
        @Test
        void __Paged() {
            final var names = new ArrayList<String>();
            var page = repositoryInstance().selectMostRentedFilms(PageRequest.of(0, 5, Sort.by("name")));
            assertThat(page.getTotalElements()).isEqualTo(16L);
            names.addAll(page.map(CategoryMostRentedFilm::getName).getContent());
            while (page.hasNext()) {
                page = repositoryInstance().selectMostRentedFilms(page.nextPageable());
                names.addAll(page.map(CategoryMostRentedFilm::getName).getContent());
            }
            assertThat(names).containsExactlyElementsOf(NAMES);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmCategoryRepository filmCategoryRepository;
}
