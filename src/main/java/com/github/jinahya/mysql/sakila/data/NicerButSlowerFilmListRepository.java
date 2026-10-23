package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.NicerButSlowerFilmList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

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
 * {@link #listAll(Pageable)} takes a {@link Pageable}, the page and the sort, and returns a {@link Page}. The sort
 * names the attributes of {@link NicerButSlowerFilmList} ({@link NicerButSlowerFilmList#ATTRIBUTE_NAME_FID},
 * {@link NicerButSlowerFilmList#ATTRIBUTE_NAME_TITLE}, ...), which the implementation maps to the columns of the view;
 * a property the view has no column for is rejected. The {@link NicerButSlowerFilmList#ATTRIBUTE_NAME_FID fid} is
 * always appended as the last sort key, so that consecutive pages neither repeat nor skip a row whatever the caller
 * sorts by. An unpaged {@code Pageable} returns every row in one page.
 * <p>
 * The methods after {@link #listAll(Pageable)} answer the exercises of upstream's {@code EXERCISES.asciidoc} on the
 * {@code nicer_but_slower_film_list} view, one method per exercise, in the order of their ids, each named in its
 * javadoc ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-01}, ...). The view has no entity, so every one of them is
 * native-only: it runs the exercise's SQL as {@code EXERCISES.asciidoc} has it, its literals, its {@code ORDER BY} and
 * its {@code LIMIT} included, from a {@code .sql} resource beside the implementation. A method whose statement selects
 * whole rows of the view, with {@code n.*}, returns them as {@link NicerButSlowerFilmList}s; one that counts or groups
 * by a value returns a single value, or its columns as one of the records nested here.
 *
 * @see NicerButSlowerFilmList
 * @see NicerButSlowerFilmListRepositoryImpl
 * @see FilmListRepository
 */
public interface NicerButSlowerFilmListRepository
        extends _NonEntityRepository<NicerButSlowerFilmList> {

    /**
     * The number of films of a category, and their average length.
     *
     * @param category      the name of the category.
     * @param films         the number of films of the category.
     * @param averageLength the average length of the films, rounded to two decimal places; {@code average_length}.
     */
    record CategoryAverageLength(String category, long films, BigDecimal averageLength) {

    }

    /**
     * The number of films of a band of lengths, with the shortest and the longest of them.
     *
     * @param band     the band: {@code short}, under an hour; {@code medium}, up to two hours; {@code long}, longer.
     * @param films    the number of films of the band.
     * @param shortest the length of the shortest film of the band, in minutes.
     * @param longest  the length of the longest film of the band, in minutes.
     */
    record LengthBand(String band, long films, Integer shortest, Integer longest) {

    }

    /**
     * The longest length of the films of a category, and how many films have it.
     *
     * @param category the name of the category.
     * @param longest  the longest length of the films of the category, in minutes.
     * @param films    the number of films of the category of that length.
     */
    record CategoryLongest(String category, Integer longest, long films) {

    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Lists every row of the view.
     * <p>
     * Not an exercise; the view's plain listing, which the tests page through.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the view.
     */
    Page<NicerButSlowerFilmList> listAll(Pageable pageable);

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Lists the actors of the film {@code 1}, as this view writes them ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-01};
     * native-only; no sort, the rows of one film), selecting the whole rows, to compare with the same row of
     * {@code film_list}.
     * <p>
     * A list rather than an optional, as {@link FilmListRepository#listActorsOfFilmOne()} explains: {@code FID} does
     * not identify a row of the view. The names are written with only the first letter of each in upper case.
     *
     * @return the rows of the film {@code 1}, each with the names of its actors in one string.
     */
    List<NicerButSlowerFilmList> listActorsOfFilmOne();

    /**
     * Counts the films {@code PENELOPE GUINESS} plays in, by the view's {@code actors}
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-02}; native-only; no sort, a single value).
     * <p>
     * The name is matched with {@code LIKE '%Penelope Guiness%'}, as this view writes it; the comparison is the
     * database's, case-insensitive by the collation of the column.
     *
     * @return the number of rows whose {@code actors} name {@code Penelope Guiness}; {@code films}.
     */
    long countFilmsOfPenelopeGuiness();

    /**
     * Lists the films which have no actors ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-B-03}; native-only; sorted by
     * {@code FID}), selecting the whole rows.
     *
     * @return the rows whose {@code actors} is {@code NULL}, by {@code FID}.
     */
    List<NicerButSlowerFilmList> listFilmsWithNoActor();

    /**
     * Lists the five categories whose films are the longest, on average
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-I-01}; native-only; sorted by {@code average_length}, descending, then
     * by {@code category}, and limited to five).
     *
     * @return the five categories of the longest average length, with the number of their films and that average.
     */
    List<CategoryAverageLength> listFiveLongestCategoriesOnAverage();

    /**
     * Counts the films which are short, medium and long ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-I-02}; native-only;
     * sorted by {@code shortest}, which puts the bands in their own order).
     * <p>
     * The band is a {@code CASE} on the length, grouped by its alias, which MySQL allows.
     *
     * @return a row per band, with the number of its films, and their shortest and longest lengths.
     */
    List<LengthBand> countFilmsByLengthBand();

    /**
     * Counts the rows of the view that differ from the same row of {@code film_list} in any column but the actors
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-A-01}; native-only; no sort, a single value, whose answer is
     * {@code 0}).
     * <p>
     * The two views are joined row by row, by {@code FID} and {@code category}, and a row counts when its
     * {@code title}, {@code description}, {@code price}, {@code length} or {@code rating} differs from the other
     * view's. The columns may be {@code NULL}, so they are compared with MySQL's null-safe {@code <=>}, under which two
     * {@code NULL}s are equal, not with {@code =}.
     *
     * @return the number of rows that differ, {@code differing}; {@code 0} when the two views differ only in how the
     *         actors are written.
     */
    long countDifferingFromFilmList();

    /**
     * Lists the longest length in each category, and how many films have it
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-A-02}; native-only; sorted by {@code category}).
     * <p>
     * The longest length is a correlated subquery over the view itself, for the category of the outer row.
     *
     * @return a row per category, with its longest length and the number of its films of that length, by category.
     */
    List<CategoryLongest> listLongestByCategory();

    /**
     * Counts the films whose {@code actors} string names a different number of actors than {@code film_actor} has
     * ({@code SAKILA-NICER_BUT_SLOWER_FILM_LIST-A-03}; native-only; no sort, a single value, whose answer is
     * {@code 0}).
     * <p>
     * The names are counted by their commas, with {@code CHAR_LENGTH}, and compared with a correlated count of the
     * film's {@code film_actor} links; a film with no actor is left out.
     *
     * @return the number of rows whose actors are miscounted, {@code mismatched}; {@code 0} when the view agrees with
     *         {@code film_actor}.
     */
    long countMismatchedActors();
}
