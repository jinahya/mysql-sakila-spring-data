package com.github.jinahya.mysql.sakila.data;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The base of the tests that run a repository in a JPA slice, over an embedded H2.
 * <p>
 * The {@code h2} profile ({@code application-h2.yaml}) replaces the MySQL datasource of
 * {@code application.yaml} with an embedded H2 in MySQL mode, rather than the H2 a bare
 * {@code @DataJpaTest} would swap in, which runs in H2's own mode and rejects the MySQL types in
 * upstream's column definitions. The JPA settings of {@code application.yaml} still apply, so the
 * H2 would get {@code ddl-auto: none} and {@code MySQLDialect}, and hold no tables.
 * {@link _Repository_DataJpaTest_Configuration} overrides those, so the tests of a subclass run
 * against a schema generated from the entities. It also turns off the {@code show_sql} that
 * {@code @DataJpaTest} turns on, so each statement is logged once, by the {@code org.hibernate.SQL}
 * logger.
 * <p>
 * {@code @DataJpaTest} is {@code @Transactional}, so each test runs in one transaction, and one
 * persistence context, which is rolled back when it ends. Nothing a test writes survives it. All
 * three annotations are inherited, so a subclass needs none of them.
 * <p>
 * The H2 starts empty, so a test persists what it queries, through {@link #fixtures()}, which saves
 * randomized entities through their repositories.
 *
 * @param <T> the type of the repository under test.
 * @param <U> the type of the entity the repository manages.
 * @param <V> the type of the entity's id.
 * @see _Repository_SpringBootIT
 */
@ActiveProfiles("h2")
@Import({_Repository_DataJpaTest_Configuration.class, _Repository_DataJpaTest_Fixtures.class})
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
    _Repository_DataJpaTest(final Class<T> repositoryClass, final Class<U> entityClass,
                            final Class<V> idClass) {
        super(repositoryClass, entityClass, idClass);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The fixtures of the slice, which persist randomized entities through their repositories.
     */
    @Autowired
    @Accessors(fluent = true)
    @Getter(AccessLevel.PACKAGE)
    private _Repository_DataJpaTest_Fixtures fixtures;
}
