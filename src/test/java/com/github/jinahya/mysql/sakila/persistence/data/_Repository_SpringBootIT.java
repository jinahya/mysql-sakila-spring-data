package com.github.jinahya.mysql.sakila.persistence.data;

import com.github.jinahya.persistence.test.util.JinahyaPersistenceTestUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * The base of the tests that run a repository against the Sakila database, through the full context of
 * {@link ___Spring_TestContext}.
 * <p>
 * Unlike a {@code @DataJpaTest}, a {@code @SpringBootTest} neither swaps in an embedded database nor wraps a test in a
 * rolled-back transaction. Each repository call runs in its own transaction, and in a persistence context of its own,
 * and commits, unless the test itself is {@code @Transactional}; then Spring's test support runs it in one transaction,
 * and rolls that back.
 * <p>
 * The tests declared here run for every subclass, against whatever rows its table holds. A table with none aborts them
 * rather than failing them; see {@link #selectRandomPresent()}.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @param <T> the type of the repository under test.
 * @param <U> the type of the entity the repository manages.
 * @param <V> the type of the entity's id.
 * @see _Repository_DataJpaTest
 */
@SpringBootTest
@Slf4j
abstract class _Repository_SpringBootIT<T extends _Repository<U, V>, U, V>
        extends __Repository_TestBase<T, U, V> {

    /**
     * Creates a new instance for the specified types.
     *
     * @param repositoryClass the class of the repository under test.
     * @param entityClass     the class of the entity the repository manages.
     * @param idClass         the class of the entity's id.
     */
    _Repository_SpringBootIT(final Class<T> repositoryClass, final Class<U> entityClass, final Class<V> idClass) {
        super(repositoryClass, entityClass, idClass);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Tests {@link _Repository#findById(Object)} against a randomly selected row.
     */
    @DisplayName("findById(ID)")
    @Nested
    class FindById_Test {

        /**
         * Selects a random entity, finds it again by its id, and asserts that the two are equal but not the same.
         * <p>
         * With no transaction around the test, the select and the find each run in a persistence context of their own,
         * so the find loads a second instance of the same row.
         */
        @Test
        void findById__() {
            final U selected = selectRandomPresent();
            final V id = identifier(selected);
            final var found = repositoryInstance().findById(id);
            assertThat(found).hasValueSatisfying(v -> {
                assertThat(v)
                        .isNotSameAs(selected)
                        .isEqualTo(selected);
            });
        }

        /**
         * Runs the same round trip in one transaction, and asserts that the find returns that very instance.
         * <p>
         * The select and the find now share the transaction's persistence context, where the selected entity is already
         * managed, so the find answers from it. The transaction is rolled back when the test ends.
         */
        @Transactional
        @Test
        void findById__Transactional() {
            final U selected = selectRandomPresent();
            final V id = identifier(selected);
            final var found = repositoryInstance().findById(id);
            assertThat(found).hasValueSatisfying(v -> {
                assertThat(v)
                        .isSameAs(selected);
            });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects a random entity of the {@link #entityClass}, through the injected entity manager.
     *
     * @return a random entity, or an empty optional if the table holds none.
     */
    Optional<U> selectRandom() {
        return JinahyaPersistenceTestUtils.selectRandom(entityManager(), entityClass);
    }

    /**
     * Selects a random entity of the {@link #entityClass}, and aborts the calling test if the table holds none.
     * <p>
     * An empty table is a missing fixture, not wrong behaviour, so the test is skipped by an assumption rather than
     * failed.
     *
     * @return a random entity.
     */
    U selectRandomPresent() {
        final var selected = selectRandom();
        assumeThat(selected).isNotEmpty();
        return selected.get();
    }
}
