package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmRepository.RatingAverageRentalRate;
import com.github.jinahya.mysql.sakila.data.FilmRepository.RatingCount;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link FilmRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. Each test persists the films it needs, through the fixtures, and
 * sets only the attributes it is about; the slice rolls them back when it ends, so a test starts over empty tables
 * and can count what it finds. A copy needs a store, which cannot be persisted into the H2 (see
 * {@link _Repository_DataJpaTest_Fixtures}), so the exercises about copies and rentals run against empty
 * {@code inventory} and {@code rental} tables here, which still proves their JPQL runs; the integration test checks
 * them against the sample data.
 *
 * @see FilmRepository_SpringBootIT
 */
class FilmRepository_DataJpaTest
        extends _Repository_DataJpaTest<FilmRepository, Film, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Film_.filmId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_FILM_ID = Sort.by(Sort.Direction.ASC, Film_.FILM_ID);

    /**
     * Creates a new instance.
     */
    FilmRepository_DataJpaTest() {
        super(FilmRepository.class, Film.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Saves a film with the specified rating, in a language of its own.
     *
     * @param rating the rating of the film.
     * @return the saved film, managed in the test's persistence context.
     */
    private Film saveFilm(final Film.Rating rating) {
        final var film = fixtures().film();
        film.setRating(rating);
        return film;
    }

    /**
     * Saves a film with the specified title and length, in a language of its own.
     * <p>
     * The saved film is managed, so the title and the length set after the save are flushed before the next query.
     *
     * @param title  the title of the film.
     * @param length the length of the film, in minutes.
     * @return the saved film, managed in the test's persistence context.
     */
    private Film saveFilm(final String title, final int length) {
        final var film = fixtures().film();
        film.setTitle(title);
        film.setLength(length);
        return film;
    }

    /**
     * Saves a film with the specified rating and rental rate, in a language of its own.
     *
     * @param rating     the rating of the film.
     * @param rentalRate the rental rate of the film, as a decimal string.
     * @return the saved film, managed in the test's persistence context.
     */
    private Film saveFilm(final Film.Rating rating, final String rentalRate) {
        final var film = saveFilm(rating);
        film.setRentalRate(new BigDecimal(rentalRate));
        return film;
    }

    /**
     * Saves a film with the specified rating and length, in a language of its own.
     *
     * @param rating the rating of the film.
     * @param length the length of the film, in minutes.
     * @return the saved film, managed in the test's persistence context.
     */
    private Film saveFilm(final Film.Rating rating, final int length) {
        final var film = saveFilm(rating);
        film.setLength(length);
        return film;
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link FilmRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The id is drawn from the whole {@code int} range, so it is all but certain to miss the small identity values
         * the H2 hands out.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves a film, and asserts that finding it by its id returns that very instance.
         * <p>
         * {@code findById} is {@code EntityManager.find}, which answers from the test's persistence context, where the
         * film is already managed, without going to the database.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = fixtures().film();
            final var found = repositoryInstance().findById(saved.getFilmId());
            assertThat(found).hasValueSatisfying(v -> {
                assertThat(v).isSameAs(saved);
            });
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmRepository#selectRatingCounts()} ({@code SAKILA-FILM-B-01}).
     */
    @Nested
    class SelectRatingCounts_Test {

        /**
         * Asserts that, with no film, the list is empty.
         */
        @Test
        void _Empty_NoFilm() {
            final var list = repositoryInstance().selectRatingCounts();
            assertThat(list).isEmpty();
        }

        /**
         * Saves one film rated {@code PG}, two rated {@code G} and one with no rating, and asserts that the list holds
         * the two ratings with their counts, and that the film with no rating is left out.
         */
        @Test
        void __() {
            saveFilm(Film.Rating.PG);
            saveFilm(Film.Rating.G);
            saveFilm(Film.Rating.G);
            saveFilm(null);
            final var list = repositoryInstance().selectRatingCounts();
            assertThat(list)
                    .extracting(RatingCount::getRating, RatingCount::getFilms)
                    .containsExactlyInAnyOrder(tuple(Film.Rating.G, 2L), tuple(Film.Rating.PG, 1L));
        }

        /**
         * Saves films rated {@code PG} and {@code G}, in that order, and asserts that the list is by rating, {@code G}
         * first, as the query orders.
         * <p>
         * {@code G} comes first by the {@code ENUM}'s order and by the alphabetical one alike, so the assertion holds
         * whichever the H2 applies.
         */
        @Test
        void __Ordered() {
            saveFilm(Film.Rating.PG);
            saveFilm(Film.Rating.G);
            final var list = repositoryInstance().selectRatingCounts();
            assertThat(list)
                    .extracting(RatingCount::getRating)
                    .containsExactly(Film.Rating.G, Film.Rating.PG);
        }
    }

    /**
     * Tests {@link FilmRepository#listLongerThan(int, Pageable)} ({@code SAKILA-FILM-B-02}, not a pick).
     */
    @Nested
    class ListLongerThan_Test {

        /**
         * Saves a film of 100 minutes, and asserts that a threshold of 180 lists an empty page, with no elements in
         * all.
         */
        @Test
        void _Empty_NoneLonger() {
            saveFilm(randomName(), 100);
            final var page = repositoryInstance().listLongerThan(180, PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves two films of 200 minutes, one of 190 and one of 100, and asserts that a threshold of 180 lists the
         * first three, the longest first and ties by title as the exercise sorts, three in all, and that pages of two
         * split them two and one.
         * <p>
         * The titles of the two longest start with {@code b} and {@code a}, so that the tie breaks the other way than
         * the ids go.
         */
        @Test
        void __() {
            final var b200 = saveFilm("b-" + randomName(), 200);
            final var a200 = saveFilm("a-" + randomName(), 200);
            final var c190 = saveFilm("c-" + randomName(), 190);
            final var d100 = saveFilm("d-" + randomName(), 100);
            final var sort = JpaSort.of(Sort.Direction.DESC, Film_.length).and(JpaSort.of(Film_.title));
            final var all = repositoryInstance().listLongerThan(180, Pageable.unpaged(sort));
            assertThat(all.getContent())
                    .containsExactly(a200, b200, c190)
                    .doesNotContain(d100)
                    .allSatisfy(f -> assertThat(f.getLength()).isGreaterThan(180));
            assertThat(all.getTotalElements()).isEqualTo(3L);
            final var page0 = repositoryInstance().listLongerThan(180, PageRequest.of(0, 2, sort));
            assertThat(page0.getContent()).containsExactly(a200, b200);
            assertThat(page0.getTotalElements()).isEqualTo(3L);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            final var page1 = repositoryInstance().listLongerThan(180, page0.nextPageable());
            assertThat(page1.getContent()).containsExactly(c190);
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
            assertThatThrownBy(() -> repositoryInstance().listLongerThan(180, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmRepository#selectAverageRentalRates()} ({@code SAKILA-FILM-B-03}).
     */
    @Nested
    class SelectAverageRentalRates_Test {

        /**
         * Asserts that, with no film, the stream is empty.
         */
        @Test
        void _Empty_NoFilm() {
            try (var stream = repositoryInstance().selectAverageRentalRates()) {
                assertThat(stream).isEmpty();
            }
        }

        /**
         * Saves films rated {@code PG} renting at {@code 0.99}, {@code 1.00} and {@code 1.00}, films rated {@code G}
         * renting at {@code 1.00} and {@code 2.00}, and a film with no rating, and asserts that the stream yields the
         * two ratings by rating, {@code G} first, with their averages rounded to two places, {@code 1.50} and
         * {@code 1.00} (from {@code 0.9966...}), and leaves the film with no rating out.
         */
        @Test
        void __() {
            saveFilm(Film.Rating.PG, "0.99");
            saveFilm(Film.Rating.PG, "1.00");
            saveFilm(Film.Rating.PG, "1.00");
            saveFilm(Film.Rating.G, "1.00");
            saveFilm(Film.Rating.G, "2.00");
            saveFilm(null, "4.99");
            try (var stream = repositoryInstance().selectAverageRentalRates()) {
                assertThat(stream.toList())
                        .extracting(RatingAverageRentalRate::getRating, RatingAverageRentalRate::getAverageRentalRate)
                        .containsExactly(tuple(Film.Rating.G, 1.50d), tuple(Film.Rating.PG, 1.00d));
            }
        }

        /**
         * Runs the query with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened. The repository would open one of its own,
         * but that one ends when the method returns, so the check runs before the query, and throws
         * {@link InvalidDataAccessApiUsageException}.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var stream = repositoryInstance().selectAverageRentalRates()) {
                    stream.forEach(r -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link FilmRepository#listWithoutInventory(Pageable)} ({@code SAKILA-FILM-I-01}).
     */
    @Nested
    class ListWithoutInventory_Test {

        /**
         * Asserts that, with no film, the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoFilm() {
            final var page = repositoryInstance().listWithoutInventory(PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves three films, and asserts that pages of two by id list them two and one, three in all, which is the
         * count the exercise asks for.
         * <p>
         * No copy can be persisted into the H2, so every film is a film with no copy; what the test proves is that
         * the {@code IS EMPTY} runs, and lists the films, not the copies.
         */
        @Test
        void __() {
            final var film1 = fixtures().film();
            final var film2 = fixtures().film();
            final var film3 = fixtures().film();
            final var page0 = repositoryInstance().listWithoutInventory(PageRequest.of(0, 2, BY_FILM_ID));
            assertThat(page0.getContent()).containsExactly(film1, film2);
            assertThat(page0.getTotalElements()).isEqualTo(3L);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            final var page1 = repositoryInstance().listWithoutInventory(page0.nextPageable());
            assertThat(page1.getContent()).containsExactly(film3);
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
            assertThatThrownBy(() -> repositoryInstance().listWithoutInventory(null))
                    .isInstanceOf(IllegalArgumentException.class);
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
         * Saves a film, and asserts that the page is empty, with no elements in all.
         * <p>
         * No copy, and so no rental, can be persisted into the H2, so no film was rented; what the test proves is
         * that the grouped query and its count query run, with the sort by the aliases.
         */
        @Test
        void _Empty_NoRental() {
            fixtures().film();
            final var page = repositoryInstance().selectRentalCounts(
                    PageRequest.of(0, 5, BY_RENTALS_DESC_THEN_FILM_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectRentalCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmRepository#selectRatingCountsOfLongerThanRatingAverage(Pageable)} ({@code SAKILA-FILM-A-01}).
     */
    @Nested
    class SelectRatingCountsOfLongerThanRatingAverage_Test {

        private static final Sort BY_RATING = Sort.by("rating");

        /**
         * Asserts that, with no film, the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoFilm() {
            final var page = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(
                    PageRequest.of(0, 5, BY_RATING));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves films rated {@code G} of 100, 120 and 140 minutes, rated {@code PG-13} of 10 and 30, and rated
         * {@code PG} of 50 and 50, and asserts that each of the first two ratings counts its one film longer than its
         * own average (120 and 20), and that {@code PG}, whose films are all as long as their average, is left out.
         */
        @Test
        void __() {
            saveFilm(Film.Rating.G, 100);
            saveFilm(Film.Rating.G, 120);
            saveFilm(Film.Rating.G, 140);
            saveFilm(Film.Rating.PG_13, 10);
            saveFilm(Film.Rating.PG_13, 30);
            saveFilm(Film.Rating.PG, 50);
            saveFilm(Film.Rating.PG, 50);
            final var page = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(
                    PageRequest.of(0, 5, BY_RATING));
            assertThat(page.getContent())
                    .extracting(RatingCount::getRating, RatingCount::getFilms)
                    .containsExactlyInAnyOrder(tuple(Film.Rating.G, 1L), tuple(Film.Rating.PG_13, 1L));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        /**
         * Saves the same films, and asserts that a page of one, by rating as the exercise sorts, holds {@code G}, two
         * in all, and the next page {@code PG-13}.
         * <p>
         * {@code G} comes before {@code PG-13} by the {@code ENUM}'s order and by the alphabetical one alike.
         */
        @Test
        void __Paged() {
            saveFilm(Film.Rating.G, 100);
            saveFilm(Film.Rating.G, 140);
            saveFilm(Film.Rating.PG_13, 10);
            saveFilm(Film.Rating.PG_13, 30);
            final var page0 = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(
                    PageRequest.of(0, 1, BY_RATING));
            assertThat(page0.getContent())
                    .extracting(RatingCount::getRating)
                    .containsExactly(Film.Rating.G);
            assertThat(page0.getTotalElements()).isEqualTo(2L);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            final var page1 = repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(page0.nextPageable());
            assertThat(page1.getContent())
                    .extracting(RatingCount::getRating)
                    .containsExactly(Film.Rating.PG_13);
        }

        /**
         * Asserts that a {@code null} page is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectRatingCountsOfLongerThanRatingAverage(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
