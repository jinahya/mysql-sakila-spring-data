package com.github.jinahya.mysql.sakila.persistence.data;

/**
 * The base of every repository of a view that upstream maps <em>without</em> {@code @Entity}, because no column, and no
 * combination of columns, identifies a row of it ({@code film_list}, {@code nicer_but_slower_film_list},
 * {@code sales_by_store}).
 * <p>
 * This is not a Spring Data repository, and no subinterface of it may be: Spring Data JPA builds repositories for
 * managed types only, and rejects such a class at startup ({@code Not a managed type}). A subinterface is a plain
 * interface, and declares the view's methods; a {@code public}, {@code @Repository} class named {@code <Interface>Impl}
 * implements it over {@link _NonEntityRepositoryImpl}. Inject it by the interface.
 * <p>
 * Nothing registers an implementation automatically: Spring Data does not, and no component scan covers this package
 * unless a consumer adds one. A consumer either component-scans this package or {@code @Import}s the class.
 * <p>
 * Declare no {@code findById}, {@code save} or {@code delete} in a subinterface: the view has no usable key, and every
 * column of it is read-only.
 *
 * @param <T> the type of the class the view is mapped to.
 * @see _NonEntityRepositoryImpl
 * @see _Repository
 */
public interface _NonEntityRepository<T> {

}
