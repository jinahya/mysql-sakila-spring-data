package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmRepository.RatingCount;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
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
     * Tests {@link FilmRepository#selectRatingCounts(Pageable)} ({@code SAKILA-FILM-B-01}).
     */
    @Nested
    class SelectRatingCounts_Test {

        private static final Sort BY_RATING = Sort.by("rating");

        /**
         * Asserts that, with no film, the page is empty, with no elements in all.
         */
        @Test
        void _Empty_NoFilm() {
            final var page = repositoryInstance().selectRatingCounts(PageRequest.of(0, 5, BY_RATING));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Saves two films rated {@code G}, one rated {@code PG} and one with no rating, and asserts that the page
         * holds the two ratings with their counts, {@code G} first as the exercise sorts, two in all, and that the
         * film with no rating is left out.
         * <p>
         * {@code G} comes first by the {@code ENUM}'s order and by the alphabetical one alike, so the assertion holds
         * whichever the H2 applies.
         */
        @Test
        void __() {
            saveFilm(Film.Rating.G);
            saveFilm(Film.Rating.G);
            saveFilm(Film.Rating.PG);
            saveFilm(null);
            final var page = repositoryInstance().selectRatingCounts(PageRequest.of(0, 5, BY_RATING));
            assertThat(page.getContent())
                    .extracting(RatingCount::getRating, RatingCount::getFilms)
                    .containsExactly(tuple(Film.Rating.G, 2L), tuple(Film.Rating.PG, 1L));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            final var first = repositoryInstance().selectRatingCounts(PageRequest.of(0, 1, BY_RATING));
            assertThat(first.getContent())
                    .extracting(RatingCount::getRating)
                    .containsExactly(Film.Rating.G);
            assertThat(first.getTotalElements()).isEqualTo(2L);
            assertThat(first.getTotalPages()).isEqualTo(2);
        }
    }

    /**
     * Tests {@link FilmRepository#listLongerThan(int, Pageable)} ({@code SAKILA-FILM-B-02}).
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
    }

    /**
     * Tests {@link FilmRepository#countWithoutInventory()} ({@code SAKILA-FILM-I-01}).
     */
    @Nested
    class CountWithoutInventory_Test {

        /**
         * Asserts that, with no film, the count is zero.
         */
        @Test
        void _Zero_NoFilm() {
            assertThat(repositoryInstance().countWithoutInventory()).isZero();
        }

        /**
         * Saves two films, and asserts that both are counted.
         * <p>
         * No copy can be persisted into the H2, so every film is a film with no copy; what the test proves is that
         * the {@code IS EMPTY} runs, and counts the films, not the copies.
         */
        @Test
        void __() {
            fixtures().film();
            fixtures().film();
            assertThat(repositoryInstance().countWithoutInventory()).isEqualTo(2L);
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
    }
}
