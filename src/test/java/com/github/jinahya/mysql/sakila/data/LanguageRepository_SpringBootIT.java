package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import com.github.jinahya.mysql.sakila.persistence.Language;
import com.github.jinahya.mysql.sakila.persistence.Language_;
import jakarta.persistence.metamodel.SingularAttribute;
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
 * Tests {@link LanguageRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is the first few
 * languages of the sample data, selected once in {@link #__()}, and each test checks a query of the exercises against
 * them, against counts of the films taken another way, or against the output upstream's
 * {@code language_exercises_*.sql} records for the installed data. The base's own tests, such as its {@code findById}
 * round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see LanguageRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LanguageRepository_SpringBootIT
        extends _Repository_SpringBootIT<LanguageRepository, Language, Integer> {

    private static List<Language> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Language_.languageId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_LANGUAGE_ID = Sort.by(Sort.Direction.ASC, Language_.LANGUAGE_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    LanguageRepository_SpringBootIT() {
        super(LanguageRepository.class, Language.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five languages, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The languages are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_LANGUAGE_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Language> languages() {
        return entities.stream();
    }

    /**
     * Counts the films whose language, or whose original language, is the specified one, through
     * {@link FilmRepository} with a {@link Specification}.
     *
     * @param language the language.
     * @param attribute the attribute of the film to match, {@link Film_#language} or {@link Film_#originalLanguage}.
     * @return the number of films.
     */
    private long countFilmsBy(final Language language, final SingularAttribute<Film, Language> attribute) {
        final Specification<Film> specification = (r, q, b) -> b.equal(r.get(attribute), language);
        return filmRepository.count(specification);
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
     * Tests {@link LanguageRepository#findAllByOrderByLanguageIdAsc()} ({@code SAKILA-LANGUAGE-B-01}).
     */
    @Nested
    class FindAllByOrderByLanguageIdAsc_Test {

        private static Stream<Language> languages() {
            return LanguageRepository_SpringBootIT.languages();
        }

        /**
         * Asserts that the list is the exercise's recorded output, the six languages by id, and every language there
         * is.
         */
        @Test
        void __() {
            final var list = repositoryInstance().findAllByOrderByLanguageIdAsc();
            assertThat(list)
                    .extracting(Language::getName)
                    .containsExactly("English", "Italian", "Japanese", "Mandarin", "French", "German");
            assertThat(list).hasSize((int) repositoryInstance().count());
        }

        /**
         * Asserts that the list is ordered by id, as the derived query orders it.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().findAllByOrderByLanguageIdAsc();
            assertThat(list).isSortedAccordingTo(Comparator.comparing(Language::getLanguageId));
        }

        /**
         * Asserts that a selected language is in the list, as a second instance of the same row, equal by id.
         *
         * @param language one of the languages selected in {@link LanguageRepository_SpringBootIT#__()}.
         */
        @MethodSource("languages")
        @ParameterizedTest
        void __Selected(final Language language) {
            final var list = repositoryInstance().findAllByOrderByLanguageIdAsc();
            assertThat(list)
                    .contains(language)
                    .noneSatisfy(l -> assertThat(l).isSameAs(language));
        }
    }

    /**
     * Tests {@link LanguageRepository#findAllByNameInOrderByNameAsc(java.util.Collection)}
     * ({@code SAKILA-LANGUAGE-B-03}).
     */
    @Nested
    class FindAllByNameInOrderByNameAsc_Test {

        private static Stream<Language> languages() {
            return LanguageRepository_SpringBootIT.languages();
        }

        /**
         * Asserts, in one transaction, that the exercise's four names stream its recorded output, the three the table
         * has, by name.
         */
        @Test
        @Transactional
        void __Transactional() {
            try (var found = repositoryInstance().findAllByNameInOrderByNameAsc(
                    List.of("English", "French", "German", "Spanish"))) {
                assertThat(found.toList())
                        .extracting(Language::getName)
                        .containsExactly("English", "French", "German");
            }
        }

        /**
         * Asserts, in one transaction, that the names of the selected languages, given in reverse, stream those
         * languages, as the instances managed in the transaction, by name.
         */
        @Test
        @Transactional
        void __Ordered() {
            final var names = entities.stream().map(Language::getName).toList().reversed();
            try (var found = repositoryInstance().findAllByNameInOrderByNameAsc(names)) {
                final var list = found.toList();
                assertThat(list)
                        .containsExactlyInAnyOrderElementsOf(entities)
                        .isSortedAccordingTo(Comparator.comparing(Language::getName));
                final var managed = repositoryInstance().findById(entities.getFirst().getLanguageId()).orElseThrow();
                assertThat(list).anySatisfy(l -> assertThat(l).isSameAs(managed));
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         *
         * @param language one of the languages selected in {@link LanguageRepository_SpringBootIT#__()}.
         */
        @MethodSource("languages")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final Language language) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByNameInOrderByNameAsc(List.of(language.getName()))) {
                    found.forEach(l -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link LanguageRepository#selectFilmCounts(Pageable)} ({@code SAKILA-LANGUAGE-I-01}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_L_LANGUAGE_ID = Sort.by("l.languageId");

        private static Stream<Language> languages() {
            return LanguageRepository_SpringBootIT.languages();
        }

        /**
         * Asserts that the languages, by id as the exercise sorts, are the exercise's recorded output: English with
         * all 1000 films, and the other five with none.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, 10, BY_L_LANGUAGE_ID));
            assertThat(page.getContent())
                    .extracting(column("language", Language.class, Language::getName), column("films", Long.class))
                    .containsExactly(tuple("English", 1000L),
                                     tuple("Italian", 0L),
                                     tuple("Japanese", 0L),
                                     tuple("Mandarin", 0L),
                                     tuple("French", 0L),
                                     tuple("German", 0L));
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the page is ordered by id, that it holds a selected language itself, and that its count is
         * the number of its films, counted by a {@link Specification} on {@link Film_#language}.
         *
         * @param language one of the languages selected in {@link LanguageRepository_SpringBootIT#__()}.
         */
        @MethodSource("languages")
        @ParameterizedTest
        void __Ordered(final Language language) {
            final var page = repositoryInstance().selectFilmCounts(Pageable.unpaged(BY_L_LANGUAGE_ID));
            assertThat(page.getContent())
                    .isSortedAccordingTo(Comparator.comparing(column("language", Language.class,
                                                                     Language::getLanguageId)))
                    .filteredOn(c -> c.get("language", Language.class).equals(language))
                    .singleElement()
                    .satisfies(c -> assertThat(c.get("films", Long.class))
                            .isEqualTo(countFilmsBy(language, Film_.language)));
        }

        /**
         * Asserts that pages of the given size, by id, walk the same languages as one unpaged page, with the same
         * total.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 4})
        @ParameterizedTest
        void __Paged(final int size) {
            final var all = repositoryInstance().selectFilmCounts(Pageable.unpaged(BY_L_LANGUAGE_ID));
            var page = repositoryInstance().selectFilmCounts(PageRequest.of(0, size, BY_L_LANGUAGE_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            final var walked = new ArrayList<Language>();
            page.forEach(c -> walked.add(c.get("language", Language.class)));
            while (page.hasNext()) {
                page = repositoryInstance().selectFilmCounts(page.nextPageable());
                page.forEach(c -> walked.add(c.get("language", Language.class)));
            }
            assertThat(walked).containsExactlyElementsOf(
                    all.getContent().stream().map(column("language", Language.class)).toList());
        }
    }

    /**
     * Tests {@link LanguageRepository#listUnused(Pageable)} ({@code SAKILA-LANGUAGE-I-02}).
     */
    @Nested
    class ListUnused_Test {

        private static Stream<Language> languages() {
            return LanguageRepository_SpringBootIT.languages();
        }

        /**
         * Asserts that the languages no film uses, by id as the exercise sorts, are the exercise's recorded output,
         * the ids {@code 2} to {@code 6}.
         */
        @Test
        void __() {
            final var page = repositoryInstance().listUnused(PageRequest.of(0, 10, BY_LANGUAGE_ID));
            assertThat(page.getContent())
                    .extracting(Language::getLanguageId, Language::getName)
                    .containsExactly(tuple(2, "Italian"),
                                     tuple(3, "Japanese"),
                                     tuple(4, "Mandarin"),
                                     tuple(5, "French"),
                                     tuple(6, "German"));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }

        /**
         * Asserts that the page is ordered by id, and that a selected language is listed exactly when no film has it
         * as its language or its original language, counted by {@link Specification}s.
         *
         * @param language one of the languages selected in {@link LanguageRepository_SpringBootIT#__()}.
         */
        @MethodSource("languages")
        @ParameterizedTest
        void __Ordered(final Language language) {
            final var page = repositoryInstance().listUnused(Pageable.unpaged(BY_LANGUAGE_ID));
            assertThat(page.getContent()).isSortedAccordingTo(Comparator.comparing(Language::getLanguageId));
            final var uses = countFilmsBy(language, Film_.language) + countFilmsBy(language, Film_.originalLanguage);
            if (uses == 0L) {
                assertThat(page.getContent()).contains(language);
            } else {
                assertThat(page.getContent()).doesNotContain(language);
            }
        }

        /**
         * Asserts that pages of two, by id, walk the same languages as one unpaged page.
         */
        @Test
        void __Paged() {
            final var all = repositoryInstance().listUnused(Pageable.unpaged(BY_LANGUAGE_ID));
            var page = repositoryInstance().listUnused(PageRequest.of(0, 2, BY_LANGUAGE_ID));
            assertPage(page);
            assertThat(page.getTotalPages()).isEqualTo(3);
            final var walked = new ArrayList<>(page.getContent());
            while (page.hasNext()) {
                page = repositoryInstance().listUnused(page.nextPageable());
                walked.addAll(page.getContent());
            }
            assertThat(walked).containsExactlyElementsOf(all.getContent());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
