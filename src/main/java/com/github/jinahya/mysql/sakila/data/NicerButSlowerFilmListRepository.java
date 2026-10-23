package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.NicerButSlowerFilmList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * A repository for {@link NicerButSlowerFilmList}, the {@code nicer_but_slower_film_list} view of the Sakila database.
 * <p>
 * The view is {@code film_list} with the names of the actors written with only the first letter of each name in upper
 * case; see {@link FilmListRepository} for the view, and for why no column identifies a row of it, so that upstream
 * maps it without {@code @Entity}. So this is not a Spring Data repository, and declares no {@code findById},
 * {@code save} or {@code delete}; see {@link _NonEntityRepository}. It is implemented by hand, in
 * {@link NicerButSlowerFilmListRepositoryImpl}, over a {@code JdbcClient}, and nothing registers that bean on its own:
 * a consumer component-scans this package or {@code @Import}s the class, and injects it by this interface.
 * <p>
 * A method that returns rows takes a {@link Pageable}, the page and the sort, and returns a {@link Page}. The sort
 * names the attributes of {@link NicerButSlowerFilmList} ({@link NicerButSlowerFilmList#ATTRIBUTE_NAME_FID},
 * {@link NicerButSlowerFilmList#ATTRIBUTE_NAME_TITLE}, ...), which the implementation maps to the columns of the view;
 * a property the view has no column for is rejected. The {@link NicerButSlowerFilmList#ATTRIBUTE_NAME_FID fid} is
 * always appended as the last sort key, so that consecutive pages neither repeat nor skip a row whatever the caller
 * sorts by. An unpaged {@code Pageable} returns every row in one page.
 * <p>
 * The methods after {@link #listAll(Pageable)} answer the exercises of upstream's {@code EXERCISES_COORDINATES.asciidoc} on the
 * {@code nicer_but_slower_film_list} view, one method per exercise, named in each method's javadoc by the exercise's
 * id ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-01}, ...). The exercises are context, not contract: an exercise that
 * counts is a {@code count...} method, and an exercise that shows rows is a {@code list...} method, which leaves the
 * order, and the {@code LIMIT}, to the caller's {@code PageRequest}.
 *
 * @see NicerButSlowerFilmList
 * @see NicerButSlowerFilmListRepositoryImpl
 * @see FilmListRepository
 */
public interface NicerButSlowerFilmListRepository
        extends _NonEntityRepository<NicerButSlowerFilmList> {

    /**
     * Lists every row of the view.
     * <p>
     * Not an exercise; the view's plain listing, which the tests select their fixture rows with.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the view.
     */
    Page<NicerButSlowerFilmList> listAll(Pageable pageable);

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Lists the rows of the view whose {@link NicerButSlowerFilmList#COLUMN_NAME_FID FID} is the specified value
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-01}, which shows the film {@code 1}, with its title and the names of
     * its actors as this view writes them, to compare with the same row of {@code film_list}).
     * <p>
     * A page rather than an optional, as {@link FilmListRepository#listByFid(Integer, Pageable)} explains: {@code FID}
     * does not identify a row of the view.
     *
     * @param fid      the {@code FID}, the id of the film, to match; must not be {@code null}.
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the film whose id is {@code fid}; empty if there is none.
     */
    Page<NicerButSlowerFilmList> listByFid(Integer fid, Pageable pageable);

    /**
     * Counts the rows of the view that differ from the same row of {@code film_list} in any column but the actors
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-A-01}, whose answer is {@code 0}).
     * <p>
     * The two views are joined row by row, by {@code FID} and {@code category}, and a row counts when its
     * {@code title}, {@code description}, {@code price}, {@code length} or {@code rating} differs from the other
     * view's.
     * The columns may be {@code NULL}, so they are compared with MySQL's null-safe {@code <=>}, under which two
     * {@code NULL}s are equal, not with {@code =}; the statement is MySQL's own, and runs on the Sakila database only.
     *
     * @return the number of rows that differ; {@code 0} when the two views differ only in how the actors are written.
     */
    long countDifferingFromFilmList();
}
