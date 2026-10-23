package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.FilmCategoryId;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
 * table, one method per exercise, named in each method's javadoc by the exercise's id
 * ({@code SAKILA-FILM_CATEGORY-I-01}, ...). The exercises are context, not contract: a method that shows rows takes
 * a {@link Pageable}, the page and the sort, and returns a {@link Page}, so the order the exercise asks for, and its
 * {@code LIMIT}, are the caller's {@code PageRequest}; each method's javadoc says which sort that is. An exercise
 * that shows columns, grouped and counted, cannot be a {@link Specification}, which only filters; such a method,
 * {@code select...}, declares its JPQL with {@link Query}, with a count query of its own since Spring Data cannot
 * derive one from a grouped select, and returns a projection, one of the interfaces declared here, which Spring Data
 * backs with the selected columns, each aliased with the name of the getter it feeds. Those aliases are what the
 * {@code Pageable} sorts by; an interface rather than a record, because Spring Data rewrites a record projection into
 * a constructor expression, which drops the aliases, and the sort with them. An exercise that counts rows of
 * another table, the films with no link, cannot be a {@link Specification} of this repository either, which filters
 * links; such a method, {@code count...}, declares its JPQL with {@link Query} and returns the count.
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
     * Selects the films which have more than the specified number of categories, with their counts
     * ({@code SAKILA-FILM_CATEGORY-I-01}, which asks for more than one, {@code 1}, and names no order; the sample data
     * has none, since every film has exactly one category).
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
     * Counts the films which have no category ({@code SAKILA-FILM_CATEGORY-I-02}; the sample data has none).
     * <p>
     * The rows counted are films, not links, so a {@link Specification} of this repository cannot express it; the
     * JPQL counts the films whose {@code filmCategories} {@code IS EMPTY}, which the provider renders as a
     * {@code NOT EXISTS} on the {@code film_category} table.
     *
     * @return the number of films with no category.
     */
    @Query("""
            SELECT COUNT(f)
            FROM Film f
            WHERE f.filmCategories IS EMPTY""")
    long countFilmsWithoutCategory();
}
