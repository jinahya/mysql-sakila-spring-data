package com.github.jinahya.mysql.sakila.persistence.data;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The base of the implementations of {@link _NonEntityRepository}, over a {@link JdbcClient}.
 * <p>
 * A subclass is a {@code public}, {@code @Repository} bean of its own, not part of any Spring Data repository; see
 * {@link _NonEntityRepository} for how it gets registered. {@code @Repository} also has Spring translate its exceptions,
 * an {@link IllegalArgumentException} included, into {@code DataAccessException}s, once a
 * {@code PersistenceExceptionTranslationPostProcessor} is in the context, as Boot puts one.
 * <p>
 * A statement is written either in place, as a text block passed to {@link #jdbcClient()}, or in a {@code .sql}
 * resource beside the subclass, read once through {@link #sql(Class, String)}, and named after the view and the query,
 * as {@code FILM_LIST_COUNT_ALL.sql}.
 * <p>
 * {@link JdbcClient} comes with what is already there: {@code spring-jdbc} through {@code spring-data-jpa} and
 * {@code spring-orm}, and the bean from Boot's auto-configuration on the single {@code DataSource}. A consumer without
 * Boot declares {@code JdbcClient.create(dataSource)} itself. Inside a transaction it shares JPA's connection but sees
 * only what JPA has flushed.
 *
 * @param <T> the type of the class the view is mapped to.
 * @see _NonEntityRepository
 */
public abstract class _NonEntityRepositoryImpl<T>
        implements _NonEntityRepository<T> {

    /**
     * Reads the statement of the specified resource, which lives in the package of the specified class.
     * <p>
     * A trailing {@code ;} is dropped. Called from a {@code static final} initializer, a missing resource fails the
     * loading of the subclass, and with it the context, rather than the first call.
     *
     * @param clazz the class whose package the resource lives in.
     * @param name  the name of the resource, relative to that package.
     * @return the statement.
     */
    protected static String sql(final Class<?> clazz, final String name) {
        Objects.requireNonNull(clazz, "clazz is null");
        Objects.requireNonNull(name, "name is null");
        final var resource = new ClassPathResource(name, clazz);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip().replaceFirst(";$", "");
        } catch (final IOException ioe) {
            throw new UncheckedIOException("failed to read " + resource, ioe);
        }
    }

    /**
     * Checks the specified page arguments.
     *
     * @param offset the number of rows to skip.
     * @param limit  the maximum number of rows to return.
     * @throws IllegalArgumentException if {@code offset} is negative, or {@code limit} is not positive.
     */
    protected static void requirePage(final long offset, final int limit) {
        if (offset < 0L) {
            throw new IllegalArgumentException("offset(" + offset + ") is negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit(" + limit + ") is not positive");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance for the specified class.
     *
     * @param targetClass the class the view is mapped to.
     */
    protected _NonEntityRepositoryImpl(final Class<T> targetClass) {
        super();
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass is null");
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The class the view is mapped to, which a row of the view maps into through its single {@code public} constructor,
     * by parameter name.
     */
    protected final Class<T> targetClass;

    /**
     * The client the statements run through.
     */
    @Autowired
    @Accessors(fluent = true)
    @Getter(AccessLevel.PROTECTED)
    private JdbcClient jdbcClient;

    /**
     * The shared, transaction-bound entity manager proxy, for the rare statement that is better as a native query of
     * the persistence unit; {@code @PersistenceContext}, as {@code @Autowired} would see two {@code EntityManager}
     * beans.
     */
    @PersistenceContext
    @Accessors(fluent = true)
    @Getter(AccessLevel.PROTECTED)
    private EntityManager entityManager;
}
