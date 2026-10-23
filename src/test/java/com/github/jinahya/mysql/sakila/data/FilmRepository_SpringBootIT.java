package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmRepository.FilmRentalCount;
import com.github.jinahya.mysql.sakila.data.FilmRepository.RatingCount;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import com.github.jinahya.mysql.sakila.persistence.Inventory_;
import com.github.jinahya.mysql.sakila.persistence.Rental;
import com.github.jinahya.mysql.sakila.persistence.Rental_;
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
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few films of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * the counts of the other repositories. The base's own tests, such as its {@code findById} round trips, run here
 * too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see FilmRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilmRepository_SpringBootIT
        extends _Repository_SpringBootIT<FilmRepository, Film, Integer> {

    private static List<Film> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Film_.filmId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_FILM_ID = Sort.by(Sort.Direction.ASC, Film_.FILM_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    FilmRepository_SpringBootIT() {
        super(FilmRepository.class, Film.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five films, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The films are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_FILM_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Film> films() {
        return entities.stream();
    }

    /**
     * Counts the films of the specified rating, through a {@link Specification}.
     *
     * @param rating the rating.
     * @return the number of films rated {@code rating}.
     */
    private long countByRating(final Film.Rating rating) {
        final Specification<Film> specification = (r, q, b) -> b.equal(r.get(Film_.rating), rating);
        return repositoryInstance().count(specification);
    }

    /**
     * Counts the rentals of the copies of the film of the specified id, through {@link RentalRepository}.
     * <p>
     * The film's own {@code inventories}, and each copy's {@code rentals}, are not public upstream, so the rentals are
     * counted by a {@link Specification} over the rental's copy's film.
     *
     * @param filmId the id of the film.
     * @return the number of rentals of the film.
     */
    private long countRentalsOf(final Integer filmId) {
        final Specification<Rental> specification =
                (r, q, b) -> b.equal(r.get(Rental_.inventory).get(Inventory_.film).get(Film_.filmId), filmId);
        return rentalRepository.count(specification);
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
     * Tests {@link FilmRepository#selectRatingCounts(Pageable)} ({@code SAKILA-FILM-B-01}).
     */
    @Nested
    class SelectRatingCounts_Test {

        private static final Sort BY_RATING = Sort.by("rating");

        private static Stream<Film> films() {
            return FilmRepository_SpringBootIT.films();
        }

        /**
         * Asserts that a page of the given size, by rating as the exercise sorts, is in the order of the
         * {@code ENUM}'s values, which is the order of {@link Film.Rating}'s constants, and that each count is the
         * number of films of that rating.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectRatingCounts(PageRequest.of(0, size, BY_RATING));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(RatingCount::getRating))
                    .allSatisfy(c -> {
                        assertThat(c.getFilms()).as("films rated %s", c.getRating())
                                .isEqualTo(countByRating(c.getRating()));
                    });
        }

        /**
         * Asserts that the rating of a selected film is among the ratings selected.
         *
         * @param film one of the films selected in {@link FilmRepository_SpringBootIT#__()}.
         */
        @MethodSource("films")
        @ParameterizedTest
        void __ContainsRatingOfFixture(final Film film) {
            assumeThat(film.getRating()).isNotNull();
            final var all = repositoryInstance().selectRatingCounts(Pageable.unpaged(BY_RATING));
            assertThat(all.getContent())
                    .extracting(RatingCount::getRating)
                    .contains(film.getRating());
        }
    }

    /**
     * Tests {@link FilmRepository#listLongerThan(int, Pageable)} ({@code SAKILA-FILM-B-02}).
     */
    @Nested
    class ListLongerThan_Test {

        /**
         * Asserts that a page of five above the given threshold, the longest first and ties by title as the exercise
         * sorts, holds films longer than the threshold only, in that order, and that it is the first five of a page
         * of ten.
         *
         * @param length the threshold, in minutes; the exercise asks for {@code 180}.
         */
        @ValueSource(ints = {120, 180})
        @ParameterizedTest
        void __(final int length) {
            final var sort = JpaSort.of(Sort.Direction.DESC, Film_.length).and(JpaSort.of(Film_.title));
            final var page = repositoryInstance().listLongerThan(length, PageRequest.of(0, 5, sort));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .allSatisfy(f -> assertThat(f.getLength()).isGreaterThan(length))
                    .isSortedAccordingTo(Comparator.comparing(Film::getLength).reversed()
                                                 .thenComparing(Film::getTitle, String.CASE_INSENSITIVE_ORDER));
            final var ten = repositoryInstance().listLongerThan(length, PageRequest.of(0, 10, sort));
            assertThat(ten.getTotalElements()).isEqualTo(page.getTotalElements());
            assertThat(ten.getContent()).startsWith(page.getContent().toArray(new Film[0]));
        }
    }

    /**
     * Tests {@link FilmRepository#countWithoutInventory()} ({@code SAKILA-FILM-I-01}).
     */
    @Nested
    class CountWithoutInventory_Test {

        /**
         * Asserts that the count is the number of all films less the number of films with a copy, which
         * {@link InventoryRepository#selectCopyCountsByFilm(Pageable)} groups.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countWithoutInventory();
            final var all = repositoryInstance().count();
            final var withCopies = inventoryRepository.selectCopyCountsByFilm(Pageable.unpaged()).getTotalElements();
            assertThat(count)
                    .isNotNegative()
                    .isEqualTo(all - withCopies);
        }
    }

    /**
     * Tests {@link FilmRepository#selectRentalCounts(Pageable)} ({@code SAKILA-FILM-I-02}).
     */
    @Nested
    class SelectRentalCounts_Test {

        private static final Sort BY_RENTALS_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "rentals").and(Sort.by("filmId"));

        /**
         * Asserts that a page of the given size, the most rented first as the exercise sorts, is ordered so, that
         * each title is the film's, and that each count is the number of rentals of the film's copies, counted
         * through {@link RentalRepository}.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectRentalCounts(
                    PageRequest.of(0, size, BY_RENTALS_DESC_THEN_FILM_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparingLong(FilmRentalCount::getRentals).reversed()
                                                 .thenComparing(FilmRentalCount::getFilmId))
                    .allSatisfy(c -> {
                        final var film = repositoryInstance().findById(c.getFilmId()).orElseThrow();
                        assertThat(film.getTitle()).isEqualTo(c.getTitle());
                        assertThat(c.getRentals()).as("rentals of film %d", c.getFilmId())
                                .isPositive()
                                .isEqualTo(countRentalsOf(c.getFilmId()));
                    });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RentalRepository rentalRepository;
}
