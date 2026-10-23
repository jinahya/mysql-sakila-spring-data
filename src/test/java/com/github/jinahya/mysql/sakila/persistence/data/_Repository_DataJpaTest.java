package com.github.jinahya.mysql.sakila.persistence.data;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * The base of the tests that run a repository in a JPA slice, over an embedded H2.
 * <p>
 * A bare {@code @DataJpaTest} implies {@code @AutoConfigureTestDatabase}, which replaces the MySQL datasource of
 * {@code application.yaml} with an embedded H2. The JPA settings of that file still apply, so the H2 would get
 * {@code ddl-auto: none} and {@code MySQLDialect}, and hold no tables. {@link _Repository_DataJpaTest_Configuration}
 * overrides those, so the tests of a subclass run against a schema generated from the entities. It also turns off the
 * {@code show_sql} that {@code @DataJpaTest} turns on, so each statement is logged once, by the {@code org.hibernate.SQL}
 * logger.
 * <p>
 * {@code @DataJpaTest} is {@code @Transactional}, so each test runs in one transaction, and one persistence context,
 * which is rolled back when it ends. Nothing a test writes survives it. Both annotations are inherited, so a subclass
 * needs neither.
 *
 * @param <T> the type of the repository under test.
 * @param <U> the type of the entity the repository manages.
 * @param <V> the type of the entity's id.
 * @see _Repository_SpringBootIT
 */
@Import(_Repository_DataJpaTest_Configuration.class)
@DataJpaTest
abstract class _Repository_DataJpaTest<T extends _Repository<U, V>, U, V>
        extends __Repository_TestBase<T, U, V> {

    /**
     * Creates a new instance for the specified types.
     *
     * @param repositoryClass the class of the repository under test.
     * @param entityClass     the class of the entity the repository manages.
     * @param idClass         the class of the entity's id.
     */
    _Repository_DataJpaTest(final Class<T> repositoryClass, final Class<U> entityClass, final Class<V> idClass) {
        super(repositoryClass, entityClass, idClass);
    }
}
