package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmText;
import com.github.jinahya.mysql.sakila.persistence.FilmText_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link FilmTextRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. The {@code film_text} rows are written by triggers of the Sakila
 * database, and the entity is read-only, so no row can be persisted into the H2 (see
 * {@link _Repository_DataJpaTest_Fixtures}): the exercises run against an empty {@code film_text} table here, which
 * still proves their JPQL runs, and the one that counts {@code film} too is checked against the films a test saves.
 * The integration test checks them against the sample data. The exercises that search with the full-text index, or
 * cut the description with {@code SUBSTRING_INDEX}, or walk the titles with a recursive {@code WITH}, are native
 * queries of MySQL's own ({@link FilmTextRepository#listMatching(String, Pageable)},
 * {@link FilmTextRepository#countMatchingInBooleanMode(String)},
 * {@link FilmTextRepository#selectEitherAndBothCounts(String, String)},
 * {@link FilmTextRepository#selectMostCommonKinds()} and {@link FilmTextRepository#selectTitleChain(int, int)}), so
 * they have no test here.
 *
 * @see FilmTextRepository_SpringBootIT
 */
class FilmTextRepository_DataJpaTest
        extends _Repository_DataJpaTest<FilmTextRepository, FilmText, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code FilmText_.filmId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_FILM_ID = Sort.by(Sort.Direction.ASC, FilmText_.FILM_ID);

    /**
     * Creates a new instance.
     */
    FilmTextRepository_DataJpaTest() {
        super(FilmTextRepository.class, FilmText.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link FilmTextRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * No row can be persisted into the H2, so no id is known; the test proves the find runs against the table.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves a film, and asserts that finding the {@code film_text} row of its id returns an empty optional.
         * <p>
         * On the Sakila database a trigger would have inserted the row; the H2 has no such trigger, and the entity is
         * read-only, so nothing does.
         */
        @Test
        void _Empty_NoTrigger() {
            final var film = fixtures().film();
            final var found = repositoryInstance().findById(film.getFilmId());
            assertThat(found).isEmpty();
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmTextRepository#findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId(String,
     * String)} ({@code SAKILA-FILM_TEXT-B-01}).
     */
    @Nested
    class FindAllByDescriptionContainingAndDescriptionContainingOrderByFilmId_Test {

        /**
         * Saves a film, and asserts that the list is empty.
         * <p>
         * No {@code film_text} row can be persisted into the H2, so none contains the words; what the test proves is
         * that the derived query, with its two {@code LIKE}s and its order, runs.
         */
        @Test
        void _Empty_NoRow() {
            fixtures().film();
            final var list = repositoryInstance()
                    .findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId("Crocodile", "Shark");
            assertThat(list).isEmpty();
        }

        /**
         * Asserts that a {@code null} word is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullWord() {
            assertThatThrownBy(() -> repositoryInstance()
                    .findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId(null, "Shark"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repositoryInstance()
                    .findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId("Crocodile", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmTextRepository#selectRowCounts()} ({@code SAKILA-FILM_TEXT-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        /**
         * Asserts that, with no film, both counts are zero, in one row.
         */
        @Test
        void _Zeros_NoFilm() {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.get("filmTexts", Long.class)).isZero();
            assertThat(counts.get("films", Long.class)).isZero();
        }

        /**
         * Saves two films, and asserts that the films are counted two and the {@code film_text} rows none.
         * <p>
         * The two counts differ here, since no trigger copies a film into {@code film_text} in the H2; which proves
         * that the two counts are of two tables.
         */
        @Test
        void __() {
            fixtures().film();
            fixtures().film();
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.get("filmTexts", Long.class)).isZero();
            assertThat(counts.get("films", Long.class)).isEqualTo(2L);
        }
    }

    /**
     * Tests {@link FilmTextRepository#listWithTitleDifferentFromFilm(Pageable)} ({@code SAKILA-FILM_TEXT-I-03}, not a
     * pick).
     */
    @Nested
    class ListWithTitleDifferentFromFilm_Test {

        /**
         * Saves a film, and asserts that the page is empty, with no elements in all.
         * <p>
         * No {@code film_text} row can be persisted into the H2, so none differs from its film; what the test proves
         * is that the join to the film runs, with the sort.
         */
        @Test
        void _Empty_NoRow() {
            fixtures().film();
            final var page = repositoryInstance().listWithTitleDifferentFromFilm(PageRequest.of(0, 5, BY_FILM_ID));
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
            assertThatThrownBy(() -> repositoryInstance().listWithTitleDifferentFromFilm(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
