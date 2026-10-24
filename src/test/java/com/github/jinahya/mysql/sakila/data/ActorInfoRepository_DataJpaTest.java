package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.ActorInfo;
import com.github.jinahya.mysql.sakila.persistence.ActorInfo_;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link ActorInfoRepository} in a JPA slice, over an embedded H2, through {@link _Repository_DataJpaTest}.
 * <p>
 * See that class for how the slice is configured. The slice generates {@code actor_info} as a table, from the entity,
 * but every column of it except the id is read-only ({@code insertable = false}), so no row can be persisted into it,
 * and the fixtures do not try. Each test here runs its query against the empty table, and asserts an empty result, or
 * a zero: that proves the JPQL, or the criteria query, is valid for Hibernate and runs on H2, which is what a slice
 * test of a view can prove. What the queries return on the sample data is for {@link ActorInfoRepository_SpringBootIT}.
 *
 * @see ActorInfoRepository_SpringBootIT
 */
class ActorInfoRepository_DataJpaTest
        extends _Repository_DataJpaTest<ActorInfoRepository, ActorInfo, Integer> {

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code ActorInfo_.actorId} is filled in only once
     * the context is up, after this class is loaded.
     */
    private static final Sort BY_ACTOR_ID = Sort.by(Sort.Direction.ASC, ActorInfo_.ACTOR_ID);

    /**
     * Creates a new instance.
     */
    ActorInfoRepository_DataJpaTest() {
        super(ActorInfoRepository.class, ActorInfo.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link ActorInfoRepository#findById(Object)}.
     */
    @Nested
    class FindById_Test {

        /**
         * Asserts that finding by a random id returns an empty optional.
         * <p>
         * The table is empty, so any id misses; the id is drawn from the whole {@code int} range all the same.
         */
        @Test
        void _Empty_Unknown() {
            final var id = ThreadLocalRandom.current().nextInt();
            final var found = repositoryInstance().findById(id);
            assertThat(found).isEmpty();
        }
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link ActorInfoRepository#selectFilmInfoHeadByActorId(int, int)} ({@code SAKILA_ACTOR_INFO_B_01}).
     */
    @Nested
    class SelectFilmInfoHeadByActorId_Test {

        /**
         * Asserts that the id of the exercise, {@code 1}, streams nothing from the empty table.
         * <p>
         * The query runs, with its {@code SUBSTRING} on the {@code film_info}, inside the test's transaction, and finds
         * no row; that it runs is what the test proves.
         */
        @Test
        void _Empty_Unknown() {
            try (var selected = repositoryInstance().selectFilmInfoHeadByActorId(1, 80)) {
                assertThat(selected).isEmpty();
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
                try (var selected = repositoryInstance().selectFilmInfoHeadByActorId(1, 80)) {
                    selected.forEach(h -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link ActorInfoRepository#countByFilmInfoCategory(String)} ({@code SAKILA_ACTOR_INFO_B_02}).
     */
    @Nested
    class CountByFilmInfoCategory_Test {

        /**
         * Asserts that the category of the exercise, {@code Horror}, counts zero on the empty table, as does a name no
         * category has.
         */
        @Test
        void _Zero_Empty() {
            assertThat(repositoryInstance().countByFilmInfoCategory("Horror")).isZero();
            assertThat(repositoryInstance().countByFilmInfoCategory(randomName())).isZero();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#findAllByFilmInfoContainingOrderByActorIdAsc(String)}
     * ({@code SAKILA_ACTOR_INFO_B_03}).
     */
    @Nested
    class FindAllByFilmInfoContainingOrderByActorIdAsc_Test {

        /**
         * Asserts that the title of the exercise, {@code ACADEMY DINOSAUR}, finds nothing in the empty table, as does a
         * title no film has.
         */
        @Test
        void _Empty_Unknown() {
            assertThat(repositoryInstance().findAllByFilmInfoContainingOrderByActorIdAsc("ACADEMY DINOSAUR")).isEmpty();
            assertThat(repositoryInstance().findAllByFilmInfoContainingOrderByActorIdAsc(randomName())).isEmpty();
        }

        /**
         * Asserts that a {@code null} title is refused with {@link IllegalArgumentException}, before any query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().findAllByFilmInfoContainingOrderByActorIdAsc(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests {@link ActorInfoRepository#selectRowCounts(Pageable)} ({@code SAKILA_ACTOR_INFO_I_01}).
     */
    @Nested
    class SelectRowCounts_Test {

        /**
         * Asserts that the select has its one row even over the empty view, and that both counts are zero: the view
         * is empty, and no actor is saved by this test.
         * <p>
         * The second count is a subquery in the select, on {@code Actor}; the test proves that Hibernate accepts it
         * there and that H2 runs it.
         */
        @Test
        void _Zero_Empty() {
            final var page = repositoryInstance().selectRowCounts(Pageable.unpaged());
            assertThat(page.getContent()).singleElement().satisfies(c -> {
                assertThat(c.get("actorInfos", Long.class)).isZero();
                assertThat(c.get("actors", Long.class)).isZero();
            });
        }

        /**
         * Asserts that a page of one holds the one row, and that the count query, which runs because the page is
         * full, counts one row in all, so there is no next page.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().selectRowCounts(PageRequest.of(0, 1));
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.hasNext()).isFalse();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#listCoveringEveryCategory(Pageable)} ({@code SAKILA_ACTOR_INFO_I_02}).
     */
    @Nested
    class ListCoveringEveryCategory_Test {

        /**
         * Asserts that the empty table lists an empty page, with no elements in all.
         * <p>
         * The criteria query runs, with its {@code LENGTH} and {@code REPLACE} on the {@code film_info} and its
         * subquery on {@code Category}; that it runs is what the test proves.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().listCoveringEveryCategory(PageRequest.of(0, 5, BY_ACTOR_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#selectActorCountsByCategoryCount()} ({@code SAKILA_ACTOR_INFO_I_03}).
     */
    @Nested
    class SelectActorCountsByCategoryCount_Test {

        /**
         * Asserts that the native query runs on H2 too, over the empty table, and selects no group.
         * <p>
         * The SQL is not MySQL's own: H2 has {@code CHAR_LENGTH} and {@code REPLACE}, and groups by a select alias.
         */
        @Test
        void _Empty_Empty() {
            assertThat(repositoryInstance().selectActorCountsByCategoryCount()).isEmpty();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#listDisagreeingOnCategories(Pageable)} ({@code SAKILA_ACTOR_INFO_A_01}).
     */
    @Nested
    class ListDisagreeingOnCategories_Test {

        /**
         * Asserts that the empty table lists an empty page, with no elements in all.
         * <p>
         * The criteria query runs, with its correlated subquery from {@code FilmActor} through {@code Film} to
         * {@code FilmCategory}; that it runs is what the test proves.
         */
        @Test
        void _Empty_Empty() {
            final var page = repositoryInstance().listDisagreeingOnCategories(PageRequest.of(0, 5, BY_ACTOR_ID));
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a {@code null} pageable is refused with {@link IllegalArgumentException}, before any query runs.
         */
        @Test
        @SuppressWarnings({"DataFlowIssue", "NullAway"})
        void _IllegalArgumentException_Null() {
            assertThatThrownBy(() -> repositoryInstance().listDisagreeingOnCategories(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
