package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import com.github.jinahya.mysql.sakila.persistence.Inventory;
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
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
     * Counts the copies of the film of the specified id, through {@link InventoryRepository}.
     * <p>
     * The film's own {@code inventories} is not public upstream, so the copies are counted by a {@link Specification}
     * over the copy's film.
     *
     * @param filmId the id of the film.
     * @return the number of copies of the film.
     */
    private long countCopiesOf(final Integer filmId) {
        final Specification<Inventory> specification =
                (r, q, b) -> b.equal(r.get(Inventory_.film).get(Film_.filmId), filmId);
        return inventoryRepository.count(specification);
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
     * Tests {@link FilmRepository#selectRatingCounts()} ({@code SAKILA_FILM_B_01}).
     */
    @Nested
    class SelectRatingCounts_Test {

        private static Stream<Film> films() {
            return FilmRepository_SpringBootIT.films();
        }

        /**
         * Asserts that the list is the exercise's output, every rating with its count, in the order of the
         * {@code ENUM}'s values, which is the order of {@link Film.Rating}'s constants, not the alphabetical one.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectRatingCounts();
            assertThat(list)
                    .extracting(column("rating", Film.Rating.class), column("films", Long.class))
                    .containsExactly(
                            tuple(Film.Rating.G, 178L),
                            tuple(Film.Rating.PG, 194L),
                            tuple(Film.Rating.PG_13, 223L),
                            tuple(Film.Rating.R, 195L),
                            tuple(Film.Rating.NC_17, 210L)
                    );
        }

        /**
         * Asserts that the list is ordered by rating, and that each count is the number of films of that rating,
         * counted through a {@link Specification}.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectRatingCounts();
            assertThat(list)
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("rating", Film.Rating.class)))
                    .allSatisfy(c -> {
                        assertThat(c.get("films", Long.class)).as("films rated %s", c.get("rating", Film.Rating.class))
                                .isEqualTo(countByRating(c.get("rating", Film.Rating.class)));
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
            final var list = repositoryInstance().selectRatingCounts();
            assertThat(list)
                    .extracting(column("rating", Film.Rating.class))
                    .contains(film.getRating());
        }
    }

    /**
     * Tests {@link FilmRepository#listLongerThan(int, Pageable)} ({@code SAKILA_FILM_B_02}, not a pick).
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

        /**
         * Asserts that the first five above 180 minutes are the exercise's output.
         */
        @Test
        void __Expected() {
            final var sort = JpaSort.of(Sort.Direction.DESC, Film_.length).and(JpaSort.of(Film_.title));
            final var page = repositoryInstance().listLongerThan(180, PageRequest.of(0, 5, sort));
            assertThat(page.getContent())
                    .extracting(Film::getFilmId)
                    .containsExactly(141, 182, 212, 349, 426);
        }
    }

    /**
     * Tests {@link FilmRepository#selectAverageRentalRates()} ({@code SAKILA_FILM_B_03}).
     */
    @Nested
    class SelectAverageRentalRates_Test {

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}.
         */
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var stream = repositoryInstance().selectAverageRentalRates()) {
                    stream.forEach(r -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts, in one transaction, that the stream yields the exercise's output, every rating with the average
         * rental rate of its films rounded to two places, in the order of the {@code ENUM}'s values.
         */
        @Test
        @Transactional
        void __Transactional() {
            try (var stream = repositoryInstance().selectAverageRentalRates()) {
                assertThat(stream.toList())
                        .extracting(column("rating", Film.Rating.class), column("averageRentalRate", Double.class))
                        .containsExactly(
                                tuple(Film.Rating.G, 2.89d),
                                tuple(Film.Rating.PG, 3.05d),
                                tuple(Film.Rating.PG_13, 3.03d),
                                tuple(Film.Rating.R, 2.94d),
                                tuple(Film.Rating.NC_17, 2.97d)
                        );
            }
        }
    }

    /**
     * Tests {@link FilmRepository#listWithoutInventory(Pageable)} ({@code SAKILA_FILM_I_01}).
     */
    @Nested
    class ListWithoutInventory_Test {

        private static Stream<Film> films() {
            return FilmRepository_SpringBootIT.films();
        }

        /**
         * Asserts that a page of the given size, by id, is ordered so, that its total is the exercise's count,
         * {@code 42}, whatever the size, and that no film listed has a copy, counted through
         * {@link InventoryRepository}.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 50})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().listWithoutInventory(PageRequest.of(0, size, BY_FILM_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(42L);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(Film::getFilmId))
                    .allSatisfy(f -> {
                        assertThat(countCopiesOf(f.getFilmId())).as("copies of film %d", f.getFilmId()).isZero();
                    });
        }

        /**
         * Asserts that a selected film is listed exactly when it has no copy.
         *
         * @param film one of the films selected in {@link FilmRepository_SpringBootIT#__()}.
         */
        @MethodSource("films")
        @ParameterizedTest
        void __Fixture(final Film film) {
            final var all = repositoryInstance().listWithoutInventory(Pageable.unpaged(BY_FILM_ID));
            if (countCopiesOf(film.getFilmId()) == 0L) {
                assertThat(all.getContent()).contains(film);
            } else {
                assertThat(all.getContent()).doesNotContain(film);
            }
        }
    }

    /**
     * Tests {@link FilmRepository#selectRentalCounts(Pageable)} ({@code SAKILA_FILM_I_02}).
     */
    @Nested
    class SelectRentalCounts_Test {

        private static final Sort BY_RENTALS_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "rentals").and(Sort.by("f.filmId"));

        /**
         * Asserts that the first five, the most rented first and then by id as the exercise sorts, are the exercise's
         * output.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectRentalCounts(
                    PageRequest.of(0, 5, BY_RENTALS_DESC_THEN_FILM_ID));
            assertThat(page.getContent())
                    .extracting(column("film", Film.class, Film::getFilmId), column("film", Film.class, Film::getTitle),
                                column("rentals", Long.class))
                    .containsExactly(
                            tuple(103, "BUCKET BROTHERHOOD", 34L),
                            tuple(738, "ROCKETEER MOTHER", 33L),
                            tuple(331, "FORWARD TEMPLE", 32L),
                            tuple(382, "GRIT CLOCKWORK", 32L),
                            tuple(489, "JUGGLER HARDLY", 32L)
                    );
        }

        /**
         * Asserts that a page of the given size, the most rented first as the exercise sorts, is ordered so, that
         * each film is the one of its id, and that each count is the number of rentals of the film's copies, counted
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
                    .isSortedAccordingTo(Comparator.comparing(column("rentals", Long.class)).reversed()
                                                 .thenComparing(column("film", Film.class, Film::getFilmId)))
                    .allSatisfy(c -> {
                        final var film = c.get("film", Film.class);
                        assertThat(repositoryInstance().findById(film.getFilmId())).hasValue(film);
                        assertThat(c.get("rentals", Long.class))
                                .as("rentals of film %d", film.getFilmId())
                                .isPositive()
                                .isEqualTo(countRentalsOf(film.getFilmId()));
                    });
        }
    }

    /**
     * Tests {@link FilmRepository#selectRatingCountsOfLongerThanRatingAverage(Pageable)} ({@code SAKILA_FILM_A_01}).
     */
    @Nested
    class SelectRatingCountsOfLongerThanRatingAverage_Test {

        private static final Sort BY_RATING = Sort.by("rating");

        /**
         * Asserts that all the ratings, by rating as the exercise sorts, are the exercise's output.
         */
        @Test
        void __Expected() {
            final var page = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(
                    Pageable.unpaged(BY_RATING));
            assertThat(page.getContent())
                    .extracting(column("rating", Film.Rating.class), column("films", Long.class))
                    .containsExactly(
                            tuple(Film.Rating.G, 83L),
                            tuple(Film.Rating.PG, 99L),
                            tuple(Film.Rating.PG_13, 118L),
                            tuple(Film.Rating.R, 94L),
                            tuple(Film.Rating.NC_17, 103L)
                    );
        }

        /**
         * Asserts that pages of the given size, by rating, walk the ratings of the unpaged list in the same order,
         * with the same total, and that each count is less than all films of the rating.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 2, 5})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(
                    Pageable.unpaged(BY_RATING));
            var page = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(
                    PageRequest.of(0, size, BY_RATING));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            final var walked = new ArrayList<Film.Rating>();
            while (true) {
                page.forEach(c -> {
                    walked.add(c.get("rating", Film.Rating.class));
                    assertThat(c.get("films", Long.class)).isPositive()
                            .isLessThan(countByRating(c.get("rating", Film.Rating.class)));
                });
                if (!page.hasNext()) {
                    break;
                }
                page = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(page.nextPageable());
            }
            assertThat(walked).containsExactlyElementsOf(all.map(column("rating", Film.Rating.class)).toList());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RentalRepository rentalRepository;
}
