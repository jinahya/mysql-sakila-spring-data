package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.persistence.util.JinahyaEntityManagerFactoryUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * The root of the repository tests of this project, whatever context they boot.
 * <p>
 * It holds the three types a repository test is about, and has the repository under test and the
 * {@link EntityManager} injected. It declares no tests and no context of its own:
 * {@link _Repository_DataJpaTest} adds a JPA slice over an embedded H2, and
 * {@link _Repository_SpringBootIT} the full context over the Sakila database.
 *
 * @param <T> the type of the repository under test.
 * @param <U> the type of the entity the repository manages.
 * @param <V> the type of the entity's id.
 */
abstract class __Repository_TestBase<T extends _Repository<U, V>, U, V> {

    /**
     * Creates a new instance for the specified types.
     *
     * @param repositoryClass the class of the repository under test.
     * @param entityClass     the class of the entity the repository manages.
     * @param idClass         the class of the entity's id.
     */
    __Repository_TestBase(final Class<T> repositoryClass, final Class<U> entityClass,
                          final Class<V> idClass) {
        super();
        this.repositoryClass = Objects.requireNonNull(repositoryClass, "repositoryClass is null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass is null");
        this.idClass = Objects.requireNonNull(idClass, "idClass is null");
    }

    // --------------------------------------------------------------------------------------------------- entityManager

    /**
     * Returns the factory of the injected {@link EntityManager}.
     *
     * @return the factory of the injected entity manager.
     */
    EntityManagerFactory entityManagerFactory() {
        return entityManager().getEntityManagerFactory();
    }

    /**
     * Returns the id of the specified entity, as the persistence unit's metamodel defines it.
     * <p>
     * This reads the id through {@link jakarta.persistence.PersistenceUnitUtil}, so it works for an
     * entity of any type, an {@code @EmbeddedId} or {@code @IdClass} one included, without knowing
     * its getter.
     *
     * @param entityInstance the entity whose id is returned.
     * @return the id of {@code entityInstance}.
     */
    V identifier(final U entityInstance) {
        return JinahyaEntityManagerFactoryUtils.getIdentifier(entityManagerFactory(),
                                                              entityInstance);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The class of the repository under test.
     */
    final Class<T> repositoryClass;

    /**
     * The class of the entity the repository manages.
     */
    final Class<U> entityClass;

    /**
     * The class of the entity's id.
     */
    final Class<V> idClass;

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The entity manager of the context, which is a shared, transaction-bound proxy: inside a
     * test's transaction it is the persistence context of that transaction, and outside one each
     * call gets a persistence context of its own.
     */
    @Autowired
    @Accessors(fluent = true)
    @Getter(AccessLevel.PACKAGE)
    private EntityManager entityManager;

    /**
     * The repository under test, injected by its type {@code T}.
     */
    @Autowired
    @Accessors(fluent = true)
    @Getter(AccessLevel.PACKAGE)
    private T repositoryInstance;
}
