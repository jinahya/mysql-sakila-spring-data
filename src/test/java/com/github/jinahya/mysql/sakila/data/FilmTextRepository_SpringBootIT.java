package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.FilmTextRepository.CategoryKindCount;
import com.github.jinahya.mysql.sakila.data.FilmTextRepository.TitleChainStep;
import com.github.jinahya.mysql.sakila.persistence.FilmText;
import com.github.jinahya.mysql.sakila.persistence.FilmText_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link FilmTextRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rows of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * the counts of the other repositories, or against the exercises' output. The exercises that search with the
 * full-text index, or cut the description with {@code SUBSTRING_INDEX}, or walk the titles with a recursive
 * {@code WITH}, are native queries of MySQL's own, so this is their only test. The base's own tests, such as its
 * {@code findById} round trips, run here too.
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
     * Tests {@link FilmTextRepository#findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId(String,
     * String)} ({@code SAKILA-FILM_TEXT-B-01}).
     */
    @Nested
    class FindAllByDescriptionContainingAndDescriptionContainingOrderByFilmId_Test {

        /**
         * Asserts that {@code Crocodile} and {@code Shark} find the exercise's output, by film id.
         */
        @Test
        void __Expected() {
            final var list = repositoryInstance()
                    .findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId("Crocodile", "Shark");
            assertThat(list)
                    .extracting(FilmText::getFilmId, FilmText::getTitle)
                    .containsExactly(
                            tuple(155, "CLEOPATRA DEVIL"),
                            tuple(177, "CONNECTICUT TRAMP"),
                            tuple(206, "DANCING FEVER"),
                            tuple(292, "EXCITEMENT EVE"),
                            tuple(429, "HONEY TIES"),
                            tuple(543, "MADIGAN DORADO"),
                            tuple(790, "SHOOTIST SUPERFLY"),
                            tuple(803, "SLACKER LIAISONS"),
                            tuple(832, "SPLASH GUMP"),
                            tuple(959, "WARLOCK WEREWOLF")
                    );
        }

        /**
         * Asserts that the two words, in either order and in any case, find the same rows, by film id, each of whose
         * descriptions contains both, ignoring case, as the database compares.
         *
         * @param word1 one word.
         * @param word2 the other word.
         */
        @CsvSource({"Crocodile,Shark", "shark,crocodile", "Boat,Dog"})
        @ParameterizedTest
        void __(final String word1, final String word2) {
            final var list = repositoryInstance()
                    .findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId(word1, word2);
            assertThat(list)
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(FilmText::getFilmId))
                    .allSatisfy(ft -> {
                        assertThat(ft.getDescription()).containsIgnoringCase(word1).containsIgnoringCase(word2);
                    });
            assertThat(repositoryInstance()
                               .findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId(word2, word1))
                    .containsExactlyElementsOf(list);
        }
    }

    /**
     * Tests {@link FilmTextRepository#selectRowCounts()} ({@code SAKILA-FILM_TEXT-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        /**
         * Asserts that the two counts are the exercise's output, {@code 1000} each.
         */
        @Test
        void __Expected() {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.getFilmTexts()).isEqualTo(1000L);
            assertThat(counts.getFilms()).isEqualTo(1000L);
        }

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
     * Tests {@link FilmTextRepository#listMatching(String, Pageable)} ({@code SAKILA-FILM_TEXT-I-02}).
     */
    @Nested
    class ListMatching_Test {

        /**
         * A sort by the {@code film_id} column, which is what a native query sorts by.
         */
        private static final Sort BY_FILM_ID_COLUMN = Sort.by(FilmText.COLUMN_NAME_FILM_ID);

        /**
         * Asserts that {@code drama} matches the exercise's count, {@code 106}, as the page's total, whatever its
         * size.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 10})
        @ParameterizedTest
        void __Expected(final int size) {
            final var page = repositoryInstance().listMatching("drama", PageRequest.of(0, size, BY_FILM_ID_COLUMN));
            assertPage(page);
            assertThat(page.getContent()).hasSize(size);
            assertThat(page.getTotalElements()).isEqualTo(106L);
        }

        /**
         * Asserts that the given word, which the sample descriptions use, matches at least one row by the full-text
         * index, and no more rows than a {@code LIKE} on the same two columns finds, which also matches the word
         * inside another; that each row listed mentions the word; and that the page is by {@code film_id}.
         *
         * @param word the word to search for; the exercise asks for {@code drama}.
         */
        @ValueSource(strings = {"drama", "documentary", "epic"})
        @ParameterizedTest
        void __(final String word) {
            final var page = repositoryInstance().listMatching(word, PageRequest.of(0, 10, BY_FILM_ID_COLUMN));
            assertPage(page);
            assertThat(page.getTotalElements())
                    .isPositive()
                    .isLessThanOrEqualTo(countContaining(word));
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(FilmText::getFilmId))
                    .allSatisfy(ft -> {
                        assertThat(ft.getTitle() + " " + ft.getDescription()).containsIgnoringCase(word);
                    });
        }

        /**
         * Asserts that a {@code null} word is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullWord() {
            assertThatThrownBy(() -> repositoryInstance().listMatching(null, PageRequest.of(0, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmTextRepository#listWithTitleDifferentFromFilm(Pageable)} ({@code SAKILA-FILM_TEXT-I-03}, not a
     * pick).
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

    /**
     * Tests {@link FilmTextRepository#countMatchingInBooleanMode(String)} ({@code SAKILA-FILM_TEXT-I-04}).
     */
    @Nested
    class CountMatchingInBooleanMode_Test {

        /**
         * Asserts that {@code +boat -shark} counts the exercise's output, {@code 143}.
         */
        @Test
        void __Expected() {
            assertThat(repositoryInstance().countMatchingInBooleanMode("+boat -shark")).isEqualTo(143L);
        }

        /**
         * Asserts that a word required alone counts no fewer rows than with another word excluded, and that the
         * two exclusive searches, with and without the other word, add up to the first.
         *
         * @param word  the word required.
         * @param other the word excluded, or required.
         */
        @CsvSource({"boat,shark", "drama,epic"})
        @ParameterizedTest
        void __(final String word, final String other) {
            final var alone = repositoryInstance().countMatchingInBooleanMode("+" + word);
            final var without = repositoryInstance().countMatchingInBooleanMode("+" + word + " -" + other);
            final var with = repositoryInstance().countMatchingInBooleanMode("+" + word + " +" + other);
            assertThat(alone).isPositive().isGreaterThanOrEqualTo(without);
            assertThat(without + with).isEqualTo(alone);
        }

        /**
         * Asserts that a {@code null} search is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullSearch() {
            assertThatThrownBy(() -> repositoryInstance().countMatchingInBooleanMode(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmTextRepository#selectEitherAndBothCounts(String, String)} ({@code SAKILA-FILM_TEXT-I-05}).
     */
    @Nested
    class SelectEitherAndBothCounts_Test {

        /**
         * Asserts that {@code crocodile} and {@code shark} count the exercise's output, {@code 203} and {@code 10}.
         */
        @Test
        void __Expected() {
            final var counts = repositoryInstance().selectEitherAndBothCounts("crocodile", "shark");
            assertThat(counts.getEitherWord()).isEqualTo(203L);
            assertThat(counts.getBothWords()).isEqualTo(10L);
        }

        /**
         * Asserts that the rows with both words are no more than those with either, and are those the boolean mode
         * counts with both required.
         *
         * @param word1 one word.
         * @param word2 the other word.
         */
        @CsvSource({"crocodile,shark", "boat,dog"})
        @ParameterizedTest
        void __(final String word1, final String word2) {
            final var counts = repositoryInstance().selectEitherAndBothCounts(word1, word2);
            assertThat(counts.getBothWords())
                    .isLessThanOrEqualTo(counts.getEitherWord())
                    .isEqualTo(repositoryInstance().countMatchingInBooleanMode("+" + word1 + " +" + word2));
        }

        /**
         * Asserts that a {@code null} word is refused, before any query runs.
         * <p>
         * The package is {@code @NullMarked}, so Spring Data checks the arguments of every repository method, a
         * {@code default} one included, and throws {@link IllegalArgumentException} before the method runs.
         */
        @Test
        void _IllegalArgumentException_NullWord() {
            assertThatThrownBy(() -> repositoryInstance().selectEitherAndBothCounts(null, "shark"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link FilmTextRepository#selectMostCommonKinds()} ({@code SAKILA-FILM_TEXT-A-01}).
     */
    @Nested
    class SelectMostCommonKinds_Test {

        /**
         * Asserts that the list is the exercise's output, by category.
         */
        @Test
        void __Expected() {
            final var list = repositoryInstance().selectMostCommonKinds();
            assertThat(list)
                    .extracting(CategoryKindCount::getCategory, CategoryKindCount::getKind,
                                CategoryKindCount::getFilms)
                    .containsExactly(
                            tuple("Action", "Reflection", 10L),
                            tuple("Animation", "Epistle", 9L),
                            tuple("Children", "Documentary", 9L),
                            tuple("Classics", "Epistle", 8L),
                            tuple("Comedy", "Panorama", 9L),
                            tuple("Documentary", "Story", 10L),
                            tuple("Drama", "Display", 12L),
                            tuple("Family", "Reflection", 11L),
                            tuple("Foreign", "Drama", 12L),
                            tuple("Games", "Display", 10L),
                            tuple("Horror", "Display", 8L),
                            tuple("Music", "Drama", 7L),
                            tuple("New", "Story", 11L),
                            tuple("Sci-Fi", "Character", 8L),
                            tuple("Sports", "Character", 11L),
                            tuple("Travel", "Drama", 9L)
                    );
        }
    }

    /**
     * Tests {@link FilmTextRepository#selectTitleChain(int, int)} ({@code SAKILA-FILM_TEXT-A-02}).
     */
    @Nested
    class SelectTitleChain_Test {

        private static Stream<FilmText> filmTexts() {
            return FilmTextRepository_SpringBootIT.filmTexts();
        }

        /**
         * Asserts that ten steps from the film {@code 1} are the exercise's output, by step.
         */
        @Test
        void __Expected() {
            final var list = repositoryInstance().selectTitleChain(1, 10);
            assertThat(list)
                    .extracting(TitleChainStep::getStep, TitleChainStep::getFilmId, TitleChainStep::getTitle)
                    .containsExactly(
                            tuple(1, 1, "ACADEMY DINOSAUR"),
                            tuple(2, 231, "DINOSAUR SECRETARY"),
                            tuple(3, 777, "SECRETARY ROUGE"),
                            tuple(4, 746, "ROUGE SQUAD"),
                            tuple(5, 836, "SQUAD FISH"),
                            tuple(6, 319, "FISH OPUS"),
                            tuple(7, 640, "OPUS ICE"),
                            tuple(8, 447, "ICE CROSSING"),
                            tuple(9, 192, "CROSSING DIVORCE"),
                            tuple(10, 237, "DIVORCE SHINING")
                    );
        }

        /**
         * Asserts that a chain of the given number of steps from a selected row starts with that row, never goes
         * past the steps, and that each title starts with the last word of a title of the step before.
         *
         * @param filmText one of the rows selected in {@link FilmTextRepository_SpringBootIT#__()}.
         */
        @MethodSource("filmTexts")
        @ParameterizedTest
        void __(final FilmText filmText) {
            final var steps = 3;
            final var list = repositoryInstance().selectTitleChain(filmText.getFilmId(), steps);
            assertThat(list).isNotEmpty();
            assertThat(list.getFirst().getStep()).isOne();
            assertThat(list.getFirst().getFilmId()).isEqualTo(filmText.getFilmId());
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparingInt(TitleChainStep::getStep))
                    .allSatisfy(s -> assertThat(s.getStep()).isBetween(1, steps));
            for (final var step : list) {
                if (step.getStep() == 1) {
                    continue;
                }
                final var first = step.getTitle().split(" ")[0];
                assertThat(list).anySatisfy(previous -> {
                    assertThat(previous.getStep()).isEqualTo(step.getStep() - 1);
                    assertThat(previous.getTitle()).endsWithIgnoringCase(" " + first);
                });
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
