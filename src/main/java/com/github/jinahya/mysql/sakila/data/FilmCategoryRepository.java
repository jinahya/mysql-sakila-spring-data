package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategoryId;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * A repository for {@link FilmCategory}, the {@code film_category} table of the Sakila database, which links a film
 * to a category.
 * <p>
 * The id is a {@link FilmCategoryId}, an {@code @IdClass} of the film's id, the basic {@link FilmCategory_#FILM_ID},
 * and the category, the association {@link FilmCategory_#CATEGORY}; {@link FilmCategory_#FILM} and
 * {@link FilmCategory_#CATEGORY_ID} map the same two columns again, read-only. A link is constructed from its film
 * and category, with {@link FilmCategory#of(com.github.jinahya.mysql.sakila.persistence.Film,
 * com.github.jinahya.mysql.sakila.persistence.Category)}, and its id is known before it is saved. The table allows a
 * film more than one category, but the sample data gives every film exactly one.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.equal(root.get(FilmCategory_.filmId), filmId)}.
 * <p>
 * {@link FilmCategory} declares named queries too ({@code FilmCategory.selectOneByFilmIdAndCategoryId},
 * {@code FilmCategory.selectListByFilmOrderByCategoryIdAsc}, {@code FilmCategory.countByCategory}, ...), but no
 * method here runs them yet. One that does is named after the query, and binds its parameters with {@code @Param}.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code film_category}
 * table, one method per exercise, in the order of the exercises' ids, each named in its javadoc by the id
 * ({@code SAKILA-FILM_CATEGORY-B-02}, ...) and by its role: the book's pick or this module's own extra. The exercises
 * are context, not contract. A Basic one is plain: no {@link Pageable}, the order the exercise asks for written in
 * the query, a {@link List} for the book's pick. An Intermediate or Advanced one takes a {@link Pageable}, the page
 * and the sort, and returns a {@link Page}, so the order the exercise asks for, and its {@code LIMIT}, are the
 * caller's {@code PageRequest}; each method's javadoc says which sort that is. An exercise that shows columns, of
 * groups or of joined tables, cannot be a {@link Specification}, which only filters the links; such a method,
 * {@code select...}, declares its JPQL with {@link Query}, with a count query of its own, and returns a projection,
 * one of the interfaces declared here, which Spring Data backs with the selected columns, each aliased with the name
 * of the getter it feeds. Those aliases are what the {@code Pageable} sorts by; an interface rather than a record,
 * because Spring Data rewrites a record projection into a constructor expression, which drops the aliases, and the
 * sort with them. An exercise about rows of another table, the films with no link, cannot be a {@link Specification}
 * of this repository either; such a method declares its JPQL with {@link Query} too, and returns those rows, whose
 * page's {@link Page#getTotalElements()} is the count the exercise asks for.
 *
 * @see FilmCategory
 * @see FilmCategory_
 */
@Repository
public interface FilmCategoryRepository
        extends _Repository<FilmCategory, FilmCategoryId> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * A film, by id, and how many categories it has: a projection of
     * {@link #selectFilmsWithMoreCategoriesThan(long, Pageable)}, sortable by {@code filmId} and {@code categories}.
     */
    interface FilmCategoryCount {

        /**
         * Returns the id of the film.
         *
         * @return the id of the film.
         */
        Integer getFilmId();

        /**
         * Returns the number of categories of the film.
         *
         * @return the number of categories.
         */
        long getCategories();
    }

    /**
     * A category, by name, and its longest film, by id, title and length: a projection of
     * {@link #selectLongestFilms(Pageable)}, sortable by {@code name}, {@code filmId}, {@code title} and
     * {@code length}.
     */
    interface CategoryLongestFilm {

        /**
         * Returns the name of the category.
         *
         * @return the name of the category.
         */
        String getName();

        /**
         * Returns the id of the film.
         *
         * @return the id of the film.
         */
        Integer getFilmId();

        /**
         * Returns the title of the film.
         *
         * @return the title of the film.
         */
        String getTitle();

        /**
         * Returns the length of the film, in minutes.
         *
         * @return the length of the film.
         */
        Integer getLength();
    }

    /**
     * A pair of categories, by name, which have the same number of films, and that number: a projection of
     * {@link #selectCategoryPairsWithSameFilmCount(Pageable)}, sortable by {@code category1}, {@code category2} and
     * {@code films}.
     */
    interface CategoryPairFilmCount {

        /**
         * Returns the name of the first category of the pair, the smaller of the two.
         *
         * @return the name of the first category.
         */
        String getCategory1();

        /**
         * Returns the name of the second category of the pair, the greater of the two.
         *
         * @return the name of the second category.
         */
        String getCategory2();

        /**
         * Returns the number of films of each of the two categories.
         *
         * @return the number of films.
         */
        long getFilms();
    }

    /**
     * Finds the links whose value of {@link FilmCategory_#FILM_ID} attribute is between the specified values,
     * inclusive ({@code SAKILA-FILM_CATEGORY-B-02}, the book's pick, which asks for the films {@code 1} to
     * {@code 10}, and orders by {@link FilmCategory_#FILM_ID}).
     * <p>
     * The query, and its order, are derived from the method name. A film of more than one category would have a link
     * per category, in no order among them; the sample data gives every film exactly one.
     *
     * @param minFilmId the smallest id of the films; must not be {@code null}.
     * @param maxFilmId the greatest id of the films; must not be {@code null}.
     * @return a list of the links of the films from {@code minFilmId} to {@code maxFilmId}, by film id.
     */
    List<FilmCategory> findAllByFilmIdBetweenOrderByFilmId(Integer minFilmId, Integer maxFilmId);

    /**
     * Selects the films which have more than the specified number of categories, with their counts
     * ({@code SAKILA-FILM_CATEGORY-I-01}, the book's pick, which asks for more than one, {@code 1}, and names no
     * order; the sample data has none, since every film has exactly one category).
     * <p>
     * The links are grouped by the film's id and counted; the filter is on the groups, so it is a {@code HAVING},
     * not a {@code WHERE}, and a {@link Specification}, which filters rows, cannot do it. The count query counts the
     * films that pass the same {@code HAVING}, through a subquery.
     *
     * @param categories the number of categories a film has to have more than.
     * @param pageable   the page to return, and its sort, by {@code filmId} or {@code categories}; must not be
     *                   {@code null}.
     * @return a page of the films with more than {@code categories} categories, with their counts.
     */
    @Query(value = """
            SELECT fc.filmId AS filmId, COUNT(fc) AS categories
            FROM FilmCategory fc
            GROUP BY fc.filmId
            HAVING COUNT(fc) > :categories""",
           countQuery = """
                   SELECT COUNT(DISTINCT fc.filmId)
                   FROM FilmCategory fc
                   WHERE fc.filmId IN (SELECT fc2.filmId
                                       FROM FilmCategory fc2
                                       GROUP BY fc2.filmId
                                       HAVING COUNT(fc2) > :categories)""")
    Page<FilmCategoryCount> selectFilmsWithMoreCategoriesThan(long categories, Pageable pageable);

    /**
     * Selects the films which have no category ({@code SAKILA-FILM_CATEGORY-I-02}, this module's own extra, which
     * asks how many; that is the page's {@link Page#getTotalElements()}, whatever its size, and the exercise names no
     * order; the sample data has none).
     * <p>
     * The rows are films, not links, so a {@link Specification} of this repository cannot express it; the JPQL
     * selects the films whose {@code filmCategories} {@code IS EMPTY}, which the provider renders as a
     * {@code NOT EXISTS} on the {@code film_category} table. The query has no grouping, so Spring Data derives its
     * count query.
     *
     * @param pageable the page to return, and its sort, by the attributes of {@link Film}; must not be {@code null}.
     * @return a page of the films with no category.
     */
    @Query("""
            SELECT f
            FROM Film f
            WHERE f.filmCategories IS EMPTY""")
    Page<Film> selectFilmsWithoutCategory(Pageable pageable);

    /**
     * Selects the longest film of each category ({@code SAKILA-FILM_CATEGORY-A-01}, this module's own extra, which
     * orders by {@code name}; of films of the same length, the one of the smallest id is the longest).
     * <p>
     * A link is kept when no other link of its category has a longer film, or a film as long with a smaller id, which
     * a correlated {@code NOT EXISTS} checks, so each category keeps one link; the category and the film are joined
     * for their columns. A category with no film is not in the page. The count query counts the links kept, through
     * the same {@code NOT EXISTS}.
     *
     * @param pageable the page to return, and its sort, by {@code name}, {@code filmId}, {@code title} or
     *                 {@code length}; must not be {@code null}.
     * @return a page of the categories, with their longest films.
     */
    @Query(value = """
            SELECT c.name AS name, f.filmId AS filmId, f.title AS title, f.length AS length
            FROM FilmCategory fc
            JOIN fc.category c
            JOIN fc.film f
            WHERE NOT EXISTS (SELECT fc2
                              FROM FilmCategory fc2
                              JOIN fc2.film f2
                              WHERE fc2.category = fc.category
                                AND (f2.length > f.length
                                     OR (f2.length = f.length AND f2.filmId < f.filmId)))""",
           countQuery = """
                   SELECT COUNT(fc)
                   FROM FilmCategory fc
                   JOIN fc.film f
                   WHERE NOT EXISTS (SELECT fc2
                                     FROM FilmCategory fc2
                                     JOIN fc2.film f2
                                     WHERE fc2.category = fc.category
                                       AND (f2.length > f.length
                                            OR (f2.length = f.length AND f2.filmId < f.filmId)))""")
    Page<CategoryLongestFilm> selectLongestFilms(Pageable pageable);

    /**
     * Selects the pairs of categories which have the same number of films, with that number
     * ({@code SAKILA-FILM_CATEGORY-A-02}, the book's pick, which orders by {@code films} descending, then by
     * {@code category1}, then by {@code category2}).
     * <p>
     * Each category is paired with every category of a greater name, so each pair is selected once, the smaller name
     * first; the first category's links are joined, grouped by the pair, and counted, and a pair is kept when the
     * count is that of the second category's links, which a correlated subquery counts. A category with no film is
     * paired with none. The count query counts the same pairs, with the two counts as two subqueries.
     *
     * @param pageable the page to return, and its sort, by {@code category1}, {@code category2} or {@code films};
     *                 must not be {@code null}.
     * @return a page of the pairs of categories with the same number of films, with that number.
     */
    @Query(value = """
            SELECT c1.name AS category1, c2.name AS category2, COUNT(fc1) AS films
            FROM Category c1
            JOIN c1.filmCategories fc1,
                 Category c2
            WHERE c1.name < c2.name
            GROUP BY c1.categoryId, c1.name, c2.categoryId, c2.name
            HAVING COUNT(fc1) = (SELECT COUNT(fc2) FROM FilmCategory fc2 WHERE fc2.category = c2)""",
           countQuery = """
                   SELECT COUNT(c1)
                   FROM Category c1, Category c2
                   WHERE c1.name < c2.name
                     AND EXISTS (SELECT fc0 FROM FilmCategory fc0 WHERE fc0.category = c1)
                     AND (SELECT COUNT(fc1) FROM FilmCategory fc1 WHERE fc1.category = c1)
                         = (SELECT COUNT(fc2) FROM FilmCategory fc2 WHERE fc2.category = c2)""")
    Page<CategoryPairFilmCount> selectCategoryPairsWithSameFilmCount(Pageable pageable);
}
