package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.persistence.util.JinahyaEntityManagerFactoryUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Tuple;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.function.Function;

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

    // ----------------------------------------------------------------------------------------------------------- tuple

    /**
     * Returns a function which reads the column of the specified alias from a {@link Tuple}, as the specified type.
     * <p>
     * A query method whose result is not an entity returns tuples, each column read by the alias the query gives it;
     * this names the read once, for {@code extracting(..)}, {@code Comparator.comparing(..)} and the like.
     * {@link Tuple#get(String, Class)} fails if the value is not of the type, so give the type the provider returns
     * ({@link Long} for a {@code COUNT}, for example).
     *
     * @param alias the alias of the column.
     * @param type  the type of the column's value.
     * @param <X>   the type of the column's value.
     * @return a function which reads the {@code alias} column of a tuple as a {@code type}.
     * @see #longColumn(String)
     */
    static <X> Function<Tuple, X> column(final String alias, final Class<X> type) {
        Objects.requireNonNull(alias, "alias is null");
        Objects.requireNonNull(type, "type is null");
        return t -> t.get(alias, type);
    }

    /**
     * Returns a function which reads the entity of the specified alias from a {@link Tuple}, and applies the specified
     * function to it, for an attribute of an entity the query selects whole
     * ({@code column("actor", Actor.class, Actor::getActorId)}).
     *
     * @param alias  the alias of the entity.
     * @param type   the type of the entity.
     * @param getter the function to apply to the entity.
     * @param <E>    the type of the entity.
     * @param <X>    the type of the result of {@code getter}.
     * @return a function which reads the {@code alias} entity of a tuple and applies {@code getter} to it.
     * @see #column(String, Class)
     */
    static <E, X> Function<Tuple, X> column(final String alias, final Class<E> type,
                                            final Function<? super E, ? extends X> getter) {
        Objects.requireNonNull(getter, "getter is null");
        return column(alias, type).andThen(getter);
    }

    /**
     * Returns a function which reads the numeric column of the specified alias from a {@link Tuple}, as a
     * {@link Long}.
     * <p>
     * A native query's numbers come back as the JDBC driver types them, which differ from a database to another
     * (MySQL gives a {@code TINYINT} as a {@link Byte}, H2 as an {@link Integer}, and so on); reading the value as a
     * {@link Number} and taking its {@code long} makes one assertion hold on both.
     *
     * @param alias the alias of the column.
     * @return a function which reads the {@code alias} column of a tuple as a {@code Long}; {@code null} for
     *         {@code NULL}.
     * @see #intColumn(String)
     */
    static Function<Tuple, Long> longColumn(final String alias) {
        Objects.requireNonNull(alias, "alias is null");
        return t -> {
            final var value = t.get(alias, Number.class);
            return value == null ? null : value.longValue();
        };
    }

    /**
     * Returns a function which reads the numeric column of the specified alias from a {@link Tuple}, as an
     * {@link Integer}, for the reason {@link #longColumn(String)} gives.
     *
     * @param alias the alias of the column.
     * @return a function which reads the {@code alias} column of a tuple as an {@code Integer}; {@code null} for
     *         {@code NULL}.
     */
    static Function<Tuple, Integer> intColumn(final String alias) {
        Objects.requireNonNull(alias, "alias is null");
        return t -> {
            final var value = t.get(alias, Number.class);
            return value == null ? null : value.intValue();
        };
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
