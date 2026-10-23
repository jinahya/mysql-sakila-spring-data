package com.github.jinahya.mysql.sakila.persistence.data;

import jakarta.persistence.EntityManagerFactory;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link ___Spring_TestContext}, in the full context.
 * <p>
 * Booting proves that the {@code @EntityScan} finds the entities one package up in the upstream jar.
 * <p>
 * It does <em>not</em> prove that the database is reachable. {@code application.yaml} names the dialect, so Hibernate
 * only logs a failed metadata connection ({@code HHH000342}) and starts anyway. Nothing here runs a query, so this test
 * passes with no Sakila database at all. The first repository test that queries it will cover that.
 *
 * @see ___Spring_TestContext
 * @see SpringTestContext_DataJpaTest
 */
@SpringBootTest
@NoArgsConstructor(access = AccessLevel.PACKAGE)
class SpringTestContext_SpringBootTest {

    /**
     * Tests the metamodel of the persistence unit that {@link ___Spring_TestContext} configures.
     */
    @Nested
    class Metamodel_Test {

        /**
         * Asserts that the persistence unit holds entities, among them the composite-key ones.
         * <p>
         * An empty metamodel would mean the scan found nothing. {@code FilmActor} ({@code @EmbeddedId}) and
         * {@code FilmCategory} ({@code @IdClass}) are named so that a mapping of either breaking the scan is noticed.
         */
        @Test
        void __() {
            final var managedTypes = entityManagerFactory.getMetamodel().getManagedTypes();
            assertThat(managedTypes)
                    .isNotEmpty()
                    .extracting(t -> t.getJavaType().getSimpleName())
                    .contains("Film", "FilmActor", "FilmCategory");
        }
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;
}
