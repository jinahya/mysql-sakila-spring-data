package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * A repository for {@link FilmList}, the {@code film_list} view of the Sakila database.
 * <p>
 * The view shows each film with its category, its price, its rating and the names of its actors in one string. No
 * column of it identifies a row: it groups by the film and the name of the category, so a film of more than one
 * category has a row for each, and upstream maps it without {@code @Entity}. So this is not a Spring Data repository,
 * and declares no {@code findById}, {@code save} or {@code delete}; see {@link _NonEntityRepository}. It is implemented
 * by hand, in {@link FilmListRepositoryImpl}, over a {@code JdbcClient}, and nothing registers that bean on its own: a
 * consumer component-scans this package or {@code @Import}s the class, and injects it by this interface.
 * <p>
 * A method that returns rows takes a {@link Pageable}, the page and the sort, and returns a {@link Page}. The sort
 * names the attributes of {@link FilmList} ({@link FilmList#ATTRIBUTE_NAME_FID}, {@link FilmList#ATTRIBUTE_NAME_TITLE},
 * ...), which the implementation maps to the columns of the view; a property the view has no column for is rejected.
 * The {@link FilmList#ATTRIBUTE_NAME_FID fid} is always appended as the last sort key, so that consecutive pages
 * neither repeat nor skip a row whatever the caller sorts by. An unpaged {@code Pageable} returns every row in one
 * page.
 * <p>
 * The methods after {@link #listAll(Pageable)} answer the exercises of upstream's {@code EXERCISES.asciidoc} on the
 * {@code film_list} view, one method per exercise, named in each method's javadoc by the exercise's id
 * ({@code SAKILA-FILM_LIST-B-01}, ...). The exercises are context, not contract: an exercise that counts is a
 * {@code count...} method, and an exercise that shows rows is a {@code list...} method, which leaves the order, and the
 * {@code LIMIT}, to the caller's {@code PageRequest}.
 *
 * @see FilmList
 * @see FilmListRepositoryImpl
 * @see NicerButSlowerFilmListRepository
 */
public interface FilmListRepository
        extends _NonEntityRepository<FilmList> {

    /**
     * Lists every row of the view.
     * <p>
     * Not an exercise; the view's plain listing, which the tests select their fixture rows with. The total of the page
     * is {@link #countAll()}.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the view.
     */
    Page<FilmList> listAll(Pageable pageable);

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Counts the rows of the view whose {@link FilmList#COLUMN_NAME_CATEGORY category} is the specified value
     * ({@code SAKILA-FILM_LIST-B-01}, which counts the {@code Action} films).
     * <p>
     * The comparison is the database's, which on the Sakila database is case-insensitive, by the collation of the
     * column, so {@code "action"} counts the {@code Action} films there.
     *
     * @param category the category to match; must not be {@code null}.
     * @return the number of rows whose category is {@code category}.
     */
    long countByCategory(String category);

    /**
     * Lists the rows of the view whose {@link FilmList#COLUMN_NAME_FID FID} is the specified value
     * ({@code SAKILA-FILM_LIST-B-02}, which shows the film {@code 1}, with its title and the names of all its actors in
     * one comma-separated string).
     * <p>
     * A page rather than an optional: {@code FID} does not identify a row of the view, which has one row for each
     * category of the film, so a film of more than one category has more than one. The sample data has none such, but
     * that is a property of the data, not of the view.
     *
     * @param fid      the {@code FID}, the id of the film, to match; must not be {@code null}.
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the film whose id is {@code fid}; empty if there is none.
     */
    Page<FilmList> listByFid(Integer fid, Pageable pageable);

    /**
     * Counts every row of the view ({@code SAKILA-FILM_LIST-I-01}, which checks that the view has a row for every film
     * which has a category).
     * <p>
     * The view joins {@code film} with {@code film_category} and {@code category}, so it has a row for a film only when
     * the film has a category. The exercise shows this count next to the number of distinct films of
     * {@code film_category}, which the caller counts for itself, through the repository of that table or of
     * {@code film}; the two are the same when every film of a category has one category.
     *
     * @return the number of rows of the view.
     */
    long countAll();
}
