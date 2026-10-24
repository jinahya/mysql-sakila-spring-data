package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

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
 * {@link #listAll(Pageable)} takes a {@link Pageable}, the page and the sort, and returns a {@link Page}. The sort
 * names the attributes of {@link FilmList} ({@link FilmList#ATTRIBUTE_NAME_FID},
 * {@link FilmList#ATTRIBUTE_NAME_TITLE}, ...), which the implementation maps to the columns of the view; a property
 * the view has no column for is rejected. The {@link FilmList#ATTRIBUTE_NAME_FID fid} is always appended as the last
 * sort key, so that consecutive pages neither repeat nor skip a row whatever the caller sorts by. An unpaged
 * {@code Pageable} returns every row in one page.
 * <p>
 * The methods after {@link #countAll()} answer the exercises of upstream's {@code EXERCISES.asciidoc} on the
 * {@code film_list} view, one method per exercise, in the order of their ids, each named in its javadoc
 * ({@code SAKILA_FILM_LIST_B_01}, ...). The view has no entity, so every one of them is native-only: it runs the
 * exercise's SQL as {@code EXERCISES.asciidoc} has it, its literals, its {@code ORDER BY} and its {@code LIMIT}
 * included, from a {@code .sql} resource beside the implementation. A method whose statement selects whole rows of
 * the view, with {@code l.*}, returns them as {@link FilmList}s; one that counts or groups by a value returns a single
 * value, or its columns as one of the records nested here.
 *
 * @see FilmList
 * @see FilmListRepositoryImpl
 * @see NicerButSlowerFilmListRepository
 */
public interface FilmListRepository
        extends _NonEntityRepository<FilmList> {

    /**
     * The number of rows of the view next to the number of films which have a category.
     *
     * @param filmList           the number of rows of the view; {@code film_list}.
     * @param filmsWithACategory the number of distinct films of {@code film_category}; {@code films_with_a_category}.
     */
    record FilmListAndFilmsWithACategory(long filmList, long filmsWithACategory) {

    }

    /**
     * The number of films of a rating at each of the three prices of the sample data.
     *
     * @param rating   the rating, as the text of MySQL's {@code ENUM} ({@code PG-13}, ...).
     * @param price099 the number of films at {@code 0.99}; {@code price_0_99}.
     * @param price299 the number of films at {@code 2.99}; {@code price_2_99}.
     * @param price499 the number of films at {@code 4.99}; {@code price_4_99}.
     */
    record RatingPrices(String rating, long price099, long price299, long price499) {

    }

    /**
     * The number of films of a category which are longer than the category's average.
     *
     * @param category          the name of the category.
     * @param longerThanAverage the number of its films longer than its average; {@code longer_than_average}.
     */
    record CategoryLongerThanAverage(String category, long longerThanAverage) {

    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Lists every row of the view.
     * <p>
     * Not an exercise; the view's plain listing, which the tests page through. The total of the page is
     * {@link #countAll()}.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows of the view.
     */
    Page<FilmList> listAll(Pageable pageable);

    /**
     * Counts every row of the view.
     * <p>
     * Not an exercise; the total of {@link #listAll(Pageable)}. {@link #countFilmListAndFilmsWithACategory()} counts
     * the same, next to what it is checked against.
     *
     * @return the number of rows of the view.
     */
    long countAll();

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Counts the {@code Action} films ({@code SAKILA_FILM_LIST_B_01}; native-only; no sort, a single value).
     * <p>
     * The view has a row for each category of a film, so this is the number of rows whose {@code category} is
     * {@code Action}; the comparison is the database's, case-insensitive by the collation of the column.
     *
     * @return the number of {@code Action} films; {@code films}.
     */
    long countActionFilms();

    /**
     * Lists the actors of the film {@code 1} ({@code SAKILA_FILM_LIST_B_02}; native-only; no sort, the rows of one
     * film), selecting the whole rows.
     * <p>
     * A list rather than an optional: {@code FID} does not identify a row of the view, which has one row for each
     * category of the film. The names come in the order the view's {@code GROUP_CONCAT} gives them, which it does not
     * fix.
     *
     * @return the rows of the film {@code 1}, each with the names of its actors in one string.
     */
    List<FilmList> listActorsOfFilmOne();

    /**
     * Lists the {@code Children} films rated {@code G} ({@code SAKILA_FILM_LIST_B_03}; native-only; sorted by
     * {@code title}, then by {@code FID}), selecting the whole rows.
     *
     * @return the rows of the {@code Children} films rated {@code G}, by title.
     */
    List<FilmList> listChildrenFilmsRatedG();

    /**
     * Lists the films with no actor ({@code SAKILA_FILM_LIST_B_04}; native-only; sorted by {@code FID}), selecting
     * the whole rows.
     * <p>
     * The view joins the actors with a {@code LEFT JOIN}, so a film with no actor is in it, with {@code NULL}
     * {@code actors}.
     *
     * @return the rows whose {@code actors} is {@code NULL}, by {@code FID}.
     */
    List<FilmList> listFilmsWithNoActor();

    /**
     * Counts the rows of the view next to the films which have a category ({@code SAKILA_FILM_LIST_I_01};
     * native-only; no sort, a single row), to check that the view has a row for every film which has a category.
     * <p>
     * The view joins {@code film} with {@code film_category} and {@code category}, so it has a row for a film only when
     * the film has a category; the two counts are the same when every film of a category has one category.
     *
     * @return the number of rows of the view and the number of distinct films of {@code film_category}.
     */
    FilmListAndFilmsWithACategory countFilmListAndFilmsWithACategory();

    /**
     * Counts the films of each rating at each price ({@code SAKILA_FILM_LIST_I_02}; native-only; sorted by
     * {@code rating}, in the order of MySQL's {@code ENUM}: {@code G}, {@code PG}, {@code PG-13}, {@code R},
     * {@code NC-17}).
     *
     * @return a row per rating, with the number of its films at {@code 0.99}, {@code 2.99} and {@code 4.99}.
     */
    List<RatingPrices> countFilmsByRatingAndPrice();

    /**
     * Lists the films whose {@code actors} string names a different number of actors than {@code film_actor} has
     * ({@code SAKILA_FILM_LIST_I_03}; native-only; sorted by {@code FID}), selecting the whole rows.
     * <p>
     * The names are counted by their commas, and compared with a correlated count of the film's {@code film_actor}
     * links; a film with no actor is left out. The answer on the sample data is no row.
     *
     * @return the rows whose actors are miscounted, by {@code FID}; empty when the view agrees with
     *         {@code film_actor}.
     */
    List<FilmList> listFilmsMiscountingActors();

    /**
     * Counts, for each category, the films longer than the category's average ({@code SAKILA_FILM_LIST_A_01};
     * native-only; sorted by {@code longer_than_average}, descending, then by {@code category}).
     * <p>
     * The average is a correlated subquery over the view itself, for the category of the outer row.
     *
     * @return a row per category, with the number of its films longer than its average, the most first.
     */
    List<CategoryLongerThanAverage> countLongerThanAverageByCategory();
}
