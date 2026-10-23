package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmText;
import com.github.jinahya.mysql.sakila.persistence.FilmText_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmTextRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rows of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * the counts of the other repositories. The exercise that searches with the full-text index is a native query of
 * MySQL's own, so this is its only test. The base's own tests, such as its {@code findById} round trips, run here
 * too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see FilmTextRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilmTextRepository_SpringBootIT
        extends _Repository_SpringBootIT<FilmTextRepository, FilmText, Integer> {

    private static List<FilmText> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code FilmText_.filmId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_FILM_ID = Sort.by(Sort.Direction.ASC, FilmText_.FILM_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    FilmTextRepository_SpringBootIT() {
        super(FilmTextRepository.class, FilmText.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rows, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The rows are loaded outside any transaction, so they are detached.
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

    private static Stream<FilmText> filmTexts() {
        return entities.stream();
    }

    /**
     * Counts the rows whose title or description contains the specified word, by a {@code LIKE}, through a
     * {@link Specification}.
     * <p>
     * A {@code LIKE} matches the word inside another, which the full-text index does not, so this count is an upper
     * bound of the index's.
     *
     * @param word the word.
     * @return the number of rows which contain {@code word}.
     */
    private long countContaining(final String word) {
        final var pattern = "%" + word + "%";
        final Specification<FilmText> specification = (r, q, b) -> b.or(
                b.like(r.get(FilmText_.title), pattern),
                b.like(r.get(FilmText_.description), pattern)
        );
        return repositoryInstance().count(specification);
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link FilmTextRepository#selectRowCounts()} ({@code SAKILA-FILM_TEXT-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        /**
         * Asserts that the count of {@code film_text} is the repository's own count, and the count of {@code film}
         * is that of {@link FilmRepository}; on the sample data the two are the same, which the triggers keep so.
         */
        @Test
        void __() {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.getFilmTexts()).isEqualTo(repositoryInstance().count());
            assertThat(counts.getFilms()).isEqualTo(filmRepository.count());
        }
    }

    /**
     * Tests {@link FilmTextRepository#countMatching(String)} ({@code SAKILA-FILM_TEXT-I-02}).
     */
    @Nested
    class CountMatching_Test {

        /**
         * Asserts that the given word, which the sample descriptions use, matches at least one row by the full-text
         * index, and no more rows than a {@code LIKE} on the same two columns finds, which also matches the word
         * inside another.
         *
         * @param word the word to search for; the exercise asks for {@code drama}.
         */
        @ValueSource(strings = {"drama", "documentary", "epic"})
        @ParameterizedTest
        void __(final String word) {
            final var count = repositoryInstance().countMatching(word);
            assertThat(count)
                    .isPositive()
                    .isLessThanOrEqualTo(countContaining(word));
        }
    }

    /**
     * Tests {@link FilmTextRepository#listWithTitleDifferentFromFilm(Pageable)} ({@code SAKILA-FILM_TEXT-I-03}).
     */
    @Nested
    class ListWithTitleDifferentFromFilm_Test {

        private static Stream<FilmText> filmTexts() {
            return FilmTextRepository_SpringBootIT.filmTexts();
        }

        /**
         * Asserts, in one transaction, that every row listed has a title other than its film's; on the sample data
         * the triggers keep the two the same, so the page is empty and the assertion on its content is vacuous.
         * <p>
         * The transaction keeps the persistence context open, so that each row's film, which is loaded lazily, can
         * be read.
         */
        @Test
        @Transactional
        void __() {
            final var page = repositoryInstance().listWithTitleDifferentFromFilm(PageRequest.of(0, 5, BY_FILM_ID));
            assertThat(page.getContent())
                    .allSatisfy(ft -> {
                        assertThat(ft.getTitle()).isNotEqualToIgnoringCase(ft.getFilm().getTitle());
                    });
        }

        /**
         * Asserts that a selected row's title is its film's, read through {@link FilmRepository}, so that the row is
         * not listed.
         *
         * @param filmText one of the rows selected in {@link FilmTextRepository_SpringBootIT#__()}.
         */
        @MethodSource("filmTexts")
        @ParameterizedTest
        void __NotContainsFixture(final FilmText filmText) {
            final var film = filmRepository.findById(filmText.getFilmId()).orElseThrow();
            assertThat(filmText.getTitle()).isEqualToIgnoringCase(film.getTitle());
            final var all = repositoryInstance().listWithTitleDifferentFromFilm(Pageable.unpaged(BY_FILM_ID));
            assertThat(all.getContent()).doesNotContain(filmText);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
