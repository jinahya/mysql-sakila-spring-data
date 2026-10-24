package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Language;
import com.github.jinahya.mysql.sakila.persistence.Language_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests {@link LanguageRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. A language, and a film in it, can be persisted into the empty H2,
 * so each test saves the languages and films it needs, with names of its own, and the slice rolls them back when it
 * ends. The names are random, and unique to the test, so a test can pick out what it saved, whatever else the H2 of
 * the cached context may hold.
 *
 * @see LanguageRepository_SpringBootIT
 */
class LanguageRepository_DataJpaTest
        extends _Repository_DataJpaTest<LanguageRepository, Language, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Language_.languageId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_LANGUAGE_ID = Sort.by(Sort.Direction.ASC, Language_.LANGUAGE_ID);

    /**
     * Creates a new instance.
     */
    LanguageRepository_DataJpaTest() {
        super(LanguageRepository.class, Language.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns a name no other test uses, cut to the twenty characters of the {@code name} column.
     *
     * @return a random name of twenty characters.
     */
    private static String randomLanguageName() {
        return randomName().substring(0, Language.SIZE_MAX_NAME);
    }

    /**
     * Saves a randomized language with the specified name, through the repository under test.
     * <p>
     * The id is {@code IDENTITY}, so the insert runs at once, and the language returned has its id; the ids of
     * successive calls ascend.
     *
     * @param name the name of the language.
     * @return the saved language, managed in the test's persistence context.
     */
    private Language saveLanguage(final String name) {
        final var language = _Repository_DataJpaTest_Fixtures.randomized(Language.class);
        language.setName(name);
        return repositoryInstance().save(language);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link LanguageRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }

        /**
         * Saves a language, and asserts that finding it by its id returns that very instance, from the test's
         * persistence context.
         */
        @Test
        void _NotEmpty_Known() {
            final var saved = saveLanguage(randomLanguageName());
            final var found = repositoryInstance().findById(saved.getLanguageId());
            assertThat(found).hasValueSatisfying(v -> assertThat(v).isSameAs(saved));
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link LanguageRepository#findAllByOrderByLanguageIdAsc()} ({@code SAKILA_LANGUAGE_B_01}).
     */
    @Nested
    class FindAllByOrderByLanguageIdAsc_Test {

        /**
         * Saves three languages, and asserts that the list holds them, those very instances, in the order they were
         * saved, which is the order of their ids.
         */
        @Test
        void __() {
            final var language1 = saveLanguage(randomLanguageName());
            final var language2 = saveLanguage(randomLanguageName());
            final var language3 = saveLanguage(randomLanguageName());
            final var list = repositoryInstance().findAllByOrderByLanguageIdAsc();
            assertThat(list)
                    .containsSubsequence(language1, language2, language3)
                    .isSortedAccordingTo((l1, l2) -> l1.getLanguageId().compareTo(l2.getLanguageId()));
            assertThat(list).anySatisfy(l -> assertThat(l).isSameAs(language1));
        }
    }

    /**
     * Tests {@link LanguageRepository#findAllByNameInOrderByNameAsc(java.util.Collection)}
     * ({@code SAKILA_LANGUAGE_B_03}).
     */
    @Nested
    class FindAllByNameInOrderByNameAsc_Test {

        /**
         * Asserts that names no language has stream nothing.
         */
        @Test
        void _Empty_Unknown() {
            try (var found = repositoryInstance().findAllByNameInOrderByNameAsc(
                    List.of(randomLanguageName(), randomLanguageName()))) {
                assertThat(found).isEmpty();
            }
        }

        /**
         * Saves three languages, and asserts that two of their names and an unknown one stream the two, by name as
         * the exercise sorts, whatever order the names are given in.
         */
        @Test
        void __() {
            final var prefix = randomLanguageName().substring(0, Language.SIZE_MAX_NAME - 1);
            final var c = saveLanguage(prefix + 'c');
            final var a = saveLanguage(prefix + 'a');
            final var b = saveLanguage(prefix + 'b');
            try (var found = repositoryInstance().findAllByNameInOrderByNameAsc(
                    List.of(c.getName(), a.getName(), randomLanguageName()))) {
                assertThat(found.toList())
                        .containsExactly(a, c)
                        .doesNotContain(b);
            }
        }

        /**
         * Runs the lookup with the test's transaction suspended, and asserts that Spring Data refuses it.
         * <p>
         * A stream has to be consumed inside a transaction the caller opened; the check runs before the query, and
         * throws {@link InvalidDataAccessApiUsageException} whether or not any language has the names.
         */
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Test
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction() {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByNameInOrderByNameAsc(List.of(randomLanguageName()))) {
                    found.forEach(l -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }

        /**
         * Asserts that a {@code null} collection of names is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings({"DataFlowIssue", "resource"})
        @Test
        void _IllegalArgumentException_NullNames() {
            assertThatThrownBy(() -> repositoryInstance().findAllByNameInOrderByNameAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link LanguageRepository#selectFilmCounts(Pageable)} ({@code SAKILA_LANGUAGE_I_01}).
     */
    @Nested
    class SelectFilmCounts_Test {

        private static final Sort BY_L_LANGUAGE_ID = Sort.by("l.languageId");

        /**
         * Saves a language with two films and one with none, and asserts that both are selected, by id as the
         * exercise sorts, the second with a count of zero, through the {@code LEFT JOIN}.
         */
        @Test
        void __() {
            final var two = saveLanguage(randomLanguageName());
            final var none = saveLanguage(randomLanguageName());
            fixtures().film(two);
            fixtures().film(two);
            final var page = repositoryInstance().selectFilmCounts(Pageable.unpaged(BY_L_LANGUAGE_ID));
            assertThat(page.getContent())
                    .extracting(column("language", Language.class), column("films", Long.class))
                    .containsSubsequence(tuple(two, 2L), tuple(none, 0L));
            assertThat(page.getContent())
                    .isSortedAccordingTo(Comparator.comparing(column("language", Language.class,
                                                                     Language::getLanguageId)));
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
        }

        /**
         * Saves three languages, and asserts that the languages are as many in all as the table holds, and that a
         * page of one, by films descending, holds the language with the most films.
         */
        @Test
        void __Paged() {
            final var most = saveLanguage(randomLanguageName());
            saveLanguage(randomLanguageName());
            saveLanguage(randomLanguageName());
            for (int i = 0; i < 3; i++) {
                fixtures().film(most);
            }
            final var page = repositoryInstance().selectFilmCounts(
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "films")));
            assertThat(page.getContent())
                    .extracting(column("language", Language.class), column("films", Long.class))
                    .containsExactly(tuple(most, 3L));
            assertThat(page.getTotalElements()).isEqualTo(repositoryInstance().count());
            assertThat(page.hasNext()).isTrue();
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before any query runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().selectFilmCounts(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link LanguageRepository#listUnused(Pageable)} ({@code SAKILA_LANGUAGE_I_02}).
     */
    @Nested
    class ListUnused_Test {

        /**
         * Saves a language no film uses, one a film is in, and one a film has as its original language, and asserts
         * that only the first is listed.
         */
        @Test
        void __() {
            final var unused = saveLanguage(randomLanguageName());
            final var used = saveLanguage(randomLanguageName());
            final var original = saveLanguage(randomLanguageName());
            fixtures().film(used).setOriginalLanguage(original);
            final var page = repositoryInstance().listUnused(Pageable.unpaged(BY_LANGUAGE_ID));
            assertThat(page.getContent())
                    .contains(unused)
                    .doesNotContain(used, original)
                    .isSortedAccordingTo((l1, l2) -> l1.getLanguageId().compareTo(l2.getLanguageId()));
        }

        /**
         * Saves three unused languages, and asserts that pages of two by id, as the exercise sorts, walk them in the
         * order they were saved.
         */
        @Test
        void __Paged() {
            final var language1 = saveLanguage(randomLanguageName());
            final var language2 = saveLanguage(randomLanguageName());
            final var language3 = saveLanguage(randomLanguageName());
            final var all = repositoryInstance().listUnused(Pageable.unpaged(BY_LANGUAGE_ID));
            assertThat(all.getContent()).containsSubsequence(language1, language2, language3);
            var page = repositoryInstance().listUnused(PageRequest.of(0, 2, BY_LANGUAGE_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            final var walked = new ArrayList<Language>(page.getContent());
            while (page.hasNext()) {
                page = repositoryInstance().listUnused(page.nextPageable());
                walked.addAll(page.getContent());
            }
            assertThat(walked).containsExactlyElementsOf(all.getContent());
        }

        /**
         * Asserts that a {@code null} pageable is refused: the package is {@code @NullMarked}, so Spring Data rejects
         * the argument with an {@link IllegalArgumentException} before the method's own check, or any query, runs.
         */
        @SuppressWarnings("DataFlowIssue")
        @Test
        void _IllegalArgumentException_NullPageable() {
            assertThatThrownBy(() -> repositoryInstance().listUnused(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
