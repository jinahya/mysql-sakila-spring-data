/**
 * Spring Data repositories for the entities of the MySQL Sakila Sample Database, which live in the sibling package
 * {@code com.github.jinahya.mysql.sakila.persistence}, in the upstream {@code mysql-sakila-persistence} artifact.
 * <p>
 * The package is {@link NullMarked}: every parameter and return type here is non-null unless marked {@code @Nullable}.
 * Spring Data reads that from the repository interfaces, and rejects a {@code null} argument to a query method with an
 * {@link IllegalArgumentException} before any query runs. A lookup that may find nothing returns an
 * {@link java.util.Optional} instead of a nullable entity.
 * <p>
 * Two kinds of repository live here:
 * <ul>
 *   <li>{@link com.github.jinahya.mysql.sakila.data._Repository}, the base of every repository of an
 *   upstream {@code @Entity}; Spring Data JPA implements its subinterfaces.</li>
 *   <li>{@link com.github.jinahya.mysql.sakila.data._NonEntityRepository}, the base of every repository of
 *   a view upstream maps <em>without</em> {@code @Entity}, implemented by hand over
 *   {@link com.github.jinahya.mysql.sakila.data._NonEntityRepositoryImpl}.</li>
 * </ul>
 */
@NullMarked
package com.github.jinahya.mysql.sakila.data;

import org.jspecify.annotations.NullMarked;
