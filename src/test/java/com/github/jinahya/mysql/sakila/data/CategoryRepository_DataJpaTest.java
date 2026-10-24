package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Category;
import com.github.jinahya.mysql.sakila.persistence.Film;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link CategoryRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the categories it needs, with the names it needs,
 * and the films of each through {@link #fixtures()}; the slice rolls them back when it ends. The names are random, and
 * unique to the test. No rental can be saved into the empty H2 (see {@link _Repository_DataJpaTest_Fixtures}), so the
 * query of the films rented the most runs against no rental.
 *
 * @see CategoryRepository_SpringBootIT
 */
class CategoryRepository_DataJpaTest
        extends _Repository_DataJpaTest<CategoryRepository, Category, Integer> {

    /**
     * Creates a new instance.
     */
    CategoryRepository_DataJpaTest() {
        super(CategoryRepository.class, Category.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns a random name of a category: the first twenty characters of a random name, within the twenty-five the
     * column holds, so that a test can append a few to order them.
     *
     * @return a random name of a category.
     */
    private static String randomCategoryName() {
        return randomName().substring(0, 20);
    }

    /**
     * Saves a category with the specified name, through the repository under test.
     *
     * @param name the name of the category.
     * @return the saved category.
     */
    private Category saveCategory(final String name) {
        final var category = _Repository_DataJpaTest_Fixtures.randomized(Category.class);
        category.setName(name);
        return repositoryInstance().save(category);
    }

    /**
     * Saves the specified number of films, each of the specified length, as films of the specified category.
     *
     * @param category the category.
     * @param films    the number of films.
     * @param length   the length of each film.
     */
    private void saveFilms(final Category category, final int films, final int length) {
        for (int i = 0; i < films; i++) {
            final Film film = fixtures().film();
            film.setLength(length);
            fixtures().filmCategory(film, category);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link CategoryRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            assertThat(repositoryInstance().findById(id)).isEmpty();
        }

        /**
         * Saves a category, and asserts that finding it by its id returns that very instance, from the test's
         * persistence context.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = saveCategory(randomCategoryName());
            assertThat(repositoryInstance().findById(saved.getCategoryId())).hasValueSatisfying(v -> {
                assertThat(v).isSameAs(saved);
            });
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link CategoryRepository#findAllByOrderByNameAsc()} ({@code SAKILA_CATEGORY_B_01}).
     */
    @Nested
    class FindAllByOrderByNameAsc_Test {

        /**
         * Saves three categories, not in the order of their names, and asserts that every category is found, by name
         * as the exercise sorts, those three in the order of their names.
         */
        @Test
        void __() {
            final var prefix = randomCategoryName();
            final var c = saveCategory(prefix + 'c');
            final var a = saveCategory(prefix + 'a');
            final var b = saveCategory(prefix + 'b');
            final var found = repositoryInstance().findAllByOrderByNameAsc();
            assertThat(found)
                    .containsSubsequence(a, b, c)
                    .isSortedAccordingTo(Comparator.comparing(Category::getName));
        }
    }

    /**
     * Tests {@link CategoryRepository#findAllByNameBetweenOrderByNameAsc(String, String)}
     * ({@code SAKILA_CATEGORY_B_02}).
     */
    @Nested
    class FindAllByNameBetweenOrderByNameAsc_Test {

        /**
         * Saves four categories of one prefix, and asserts that a range of the second to the third name streams those
         * two, both ends included, by name as the exercise sorts.
         */
        @Test
        void __() {
            final var prefix = randomCategoryName();
            final var a = saveCategory(prefix + 'a');
            final var c = saveCategory(prefix + 'c');
            final var b = saveCategory(prefix + 'b');
            final var d = saveCategory(prefix + 'd');
            try (var found = repositoryInstance().findAllByNameBetweenOrderByNameAsc(prefix + 'b', prefix + 'c')) {
                assertThat(found.toList())
                        .containsExactly(b, c)
                        .doesNotContain(a, d);
            }
        }

        /**
         * Asserts that a range no name falls in streams nothing.
         */
        @Test
        void _Empty_Unknown() {
            final var prefix = randomCategoryName();
            try (var found = repositoryInstance().findAllByNameBetweenOrderByNameAsc(prefix + 'a', prefix + 'b')) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByNameBetweenOrderByNameAsc("D", "H")) {
                    found.forEach(c -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} end of the range is refused with {@link IllegalArgumentException}, before any
         * query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().findAllByNameBetweenOrderByNameAsc(null, "H"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repositoryInstance().findAllByNameBetweenOrderByNameAsc("D", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link CategoryRepository#selectFilmCounts(Pageable)} ({@code SAKILA_CATEGORY_I_01}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_NAME =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("c.name"));

        /**
         * Saves categories of three, two and no films, and asserts that a page of one, the most films first, holds the
         * first, that the categories with a film are two in all, and that the next page holds the second; the one with
         * no film is never selected.
         */
        @Test
        void __() {
            final var prefix = randomCategoryName();
            final var three = saveCategory(prefix + 'a');
            final var two = saveCategory(prefix + 'b');
            final var none = saveCategory(prefix + 'c');
            saveFilms(three, 3, 100);
            saveFilms(two, 2, 100);
            final var page0 = repositoryInstance().selectFilmCounts(PageRequest.of(0, 1, BY_FILMS_DESC_THEN_NAME));
            assertThat(page0.getContent())
                    .extracting(column("category", Category.class), column("films", Long.class))
                    .containsExactly(tuple(three, 3L));
            assertThat(page0.getTotalElements()).isEqualTo(2L);
            final var page1 = repositoryInstance().selectFilmCounts(page0.nextPageable());
            assertThat(page1.getContent())
                    .extracting(column("category", Category.class), column("films", Long.class))
                    .containsExactly(tuple(two, 2L));
            assertThat(repositoryInstance().selectFilmCounts(Pageable.unpaged()).getContent())
                    .extracting(column("category", Category.class))
                    .doesNotContain(none);
        }
    }

    /**
     * Tests {@link CategoryRepository#selectAverageLengths(Pageable)} ({@code SAKILA_CATEGORY_I_02}).
     */
    @Nested
    class SelectAverageLengths_Test {

        private static final Sort BY_AVERAGE_LENGTH_DESC = Sort.by(Sort.Direction.DESC, "averageLength");

        /**
         * Saves a category of films of {@code 100} and {@code 121} minutes and one of a film of {@code 90}, and
         * asserts that a page of one, the longest average first as the exercise takes it, holds the first, with
         * {@code 110.5}, of two in all.
         */
        @Test
        void __() {
            final var longer = saveCategory(randomCategoryName());
            final var shorter = saveCategory(randomCategoryName());
            saveFilms(longer, 1, 100);
            saveFilms(longer, 1, 121);
            saveFilms(shorter, 1, 90);
            final var page = repositoryInstance().selectAverageLengths(PageRequest.of(0, 1, BY_AVERAGE_LENGTH_DESC));
            assertThat(page.getContent())
                    .extracting(column("category", Category.class), column("averageLength", Double.class))
                    .containsExactly(tuple(longer, 110.5d));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            final var next = repositoryInstance().selectAverageLengths(page.nextPageable());
            assertThat(next.getContent())
                    .extracting(column("category", Category.class), column("averageLength", Double.class))
                    .containsExactly(tuple(shorter, 90.0d));
        }
    }

    /**
     * Tests {@link CategoryRepository#selectRunningFilmCounts(Pageable)} ({@code SAKILA_CATEGORY_A_01}).
     */
    @Nested
    class SelectRunningFilmCounts_Test {

        private static final Sort BY_FILMS_DESC_THEN_NAME =
                Sort.by(Sort.Direction.DESC, "films").and(Sort.by("c.name"));

        /**
         * Saves categories of two, one and one films, and one of none, and asserts that, the most films first and then
         * by name as the exercise sorts, the running total runs two, three, four, of four films, the percentages with
         * it, and that the category of no film is not selected.
         */
        @Test
        void __() {
            final var prefix = randomCategoryName();
            final var two = saveCategory(prefix + 'a');
            final var oneB = saveCategory(prefix + 'c');
            final var oneA = saveCategory(prefix + 'b');
            final var none = saveCategory(prefix + 'd');
            saveFilms(two, 2, 100);
            saveFilms(oneB, 1, 100);
            saveFilms(oneA, 1, 100);
            final var page = repositoryInstance().selectRunningFilmCounts(Pageable.unpaged(BY_FILMS_DESC_THEN_NAME));
            assertThat(page.getContent())
                    .extracting(column("category", Category.class), column("films", Long.class),
                                column("runningFilms", Long.class), column("runningPercent", Double.class))
                    .containsExactly(
                            tuple(two, 2L, 2L, 50.0d),
                            tuple(oneA, 1L, 3L, 75.0d),
                            tuple(oneB, 1L, 4L, 100.0d)
                    )
                    .doesNotContain(tuple(none, 0L, 0L, 0.0d));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        /**
         * Saves three categories of a film each, and asserts that pages of two split them two and one, three in all,
         * which the count query says.
         */
        @Test
        void __Paged() {
            for (int i = 0; i < 3; i++) {
                saveFilms(saveCategory(randomCategoryName()), 1, 100);
            }
            final var page0 = repositoryInstance().selectRunningFilmCounts(
                    PageRequest.of(0, 2, BY_FILMS_DESC_THEN_NAME));
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isEqualTo(3L);
            final var page1 = repositoryInstance().selectRunningFilmCounts(page0.nextPageable());
            assertThat(page1.getContent()).hasSize(1);
        }
    }

    /**
     * Tests {@link CategoryRepository#selectMostRentedFilms(Pageable)} ({@code SAKILA_CATEGORY_A_02}).
     */
    @Nested
    class SelectMostRentedFilms_Test {

        /**
         * Saves a category of a film, which no one can have rented in the H2, and asserts that the page is empty, with
         * no elements in all.
         * <p>
         * The query runs, with its two {@code HAVING ... ALL} subqueries, and finds no rental; that it runs on H2 is
         * what the test proves.
         */
        @Test
        void _Empty_NoRental() {
            saveFilms(saveCategory(randomCategoryName()), 1, 100);
            final var page = repositoryInstance().selectMostRentedFilms(PageRequest.of(0, 5, Sort.by("c.name")));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }
}
