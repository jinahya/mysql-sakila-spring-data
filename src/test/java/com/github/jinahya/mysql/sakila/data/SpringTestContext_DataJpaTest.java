package com.github.jinahya.mysql.sakila.data;

import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link ___Spring_TestContext}, in the JPA slice over the embedded H2 of the {@code h2} profile.
 * <p>
 * Unlike {@link SpringTestContext_SpringBootTest}, this does reach a database: {@link _Repository_DataJpaTest_Configuration}
 * has Hibernate generate the schema of every entity into the H2, so a mapping the H2 dialect cannot express fails the
 * context, and the query below proves the generated tables answer.
 *
 * @see ___Spring_TestContext
 * @see SpringTestContext_SpringBootTest
 */
@ActiveProfiles("h2")
@Import(_Repository_DataJpaTest_Configuration.class)
@DataJpaTest
@NoArgsConstructor(access = AccessLevel.PACKAGE)
class SpringTestContext_DataJpaTest {

    /**
     * Tests the schema generated into the H2.
     */
    @Nested
    class Schema_Test {

        /**
         * Asserts that each entity's table exists and is empty: the H2 starts from nothing, and no test writes to it
         * outside its own rolled-back transaction.
         */
        @Test
        void __() {
            final var metamodel = entityManager.getEntityManagerFactory().getMetamodel();
            assertThat(metamodel.getEntities()).isNotEmpty();
            metamodel.getEntities().forEach(e -> {
                final var count = entityManager
                        .createQuery("SELECT COUNT(e) FROM " + e.getName() + " e", Long.class)
                        .getSingleResult();
                assertThat(count).as(e.getName()).isZero();
            });
        }
    }

    @Autowired
    private EntityManager entityManager;
}
