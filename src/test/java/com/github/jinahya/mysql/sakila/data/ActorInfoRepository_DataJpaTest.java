package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.ActorInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.jinahya.mysql.sakila.data._Repository_DataJpaTest_Fixtures.randomName;
import static org.assertj.core.api.Assertions.assertThat;

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
     * Tests {@link ActorInfoRepository#selectFilmInfoHeadByActorId(int, int)} ({@code SAKILA-ACTOR_INFO-B-01}).
     */
    @Nested
    class SelectFilmInfoHeadByActorId_Test {

        /**
         * Asserts that the id of the exercise, {@code 1}, selects nothing from the empty table.
         * <p>
         * The query runs, with its {@code SUBSTRING} on the {@code film_info}, and finds no row; that it runs is what
         * the test proves.
         */
        @Test
        void _Empty_Unknown() {
            final var selected = repositoryInstance().selectFilmInfoHeadByActorId(1, 80);
            assertThat(selected).isEmpty();
        }
    }

    /**
     * Tests {@link ActorInfoRepository#countByFilmInfoCategory(String)} ({@code SAKILA-ACTOR_INFO-B-02}).
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
     * Tests {@link ActorInfoRepository#selectRowCounts()} ({@code SAKILA-ACTOR_INFO-I-01}).
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
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts).isNotNull();
            assertThat(counts.getActorInfos()).isZero();
            assertThat(counts.getActors()).isZero();
        }
    }
}
