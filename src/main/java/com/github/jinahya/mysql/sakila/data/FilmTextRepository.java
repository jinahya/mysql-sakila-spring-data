package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmText;
import com.github.jinahya.mysql.sakila.persistence.FilmText_;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * A repository for {@link FilmText}, the {@code film_text} table of the Sakila database, which keeps a copy of each
 * film's title and description for the full-text index.
 * <p>
 * The id is the film's id, an {@link Integer} which is not generated: triggers on the {@code film} table insert,
 * update and delete the rows, so the entity is read-only, every column {@code insertable = false, updatable =
 * false}, and nothing should be saved through this repository. {@link FilmText_#FILM} is the film the row copies,
 * mapped over the same column as the id.
 * <p>
 * {@link _Repository} brings {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor} for criteria
 * queries. Name the attributes through the generated static metamodel rather than by string, for example
 * {@code (root, query, builder) -> builder.equal(root.get(FilmText_.title), title)}.
 * <p>
 * {@link FilmText} declares named queries too ({@code FilmText.selectOneByFilmId},
 * {@code FilmText.selectListOrderByFilmIdAsc}, {@code FilmText.selectMaxFilmId}, {@code FilmText.countAll}, ...),
 * but no method here runs them yet. One that does is named after the query, and binds its parameters with
 * {@code @Param}.
 * <p>
 * The methods below answer the exercises of upstream's {@code EXERCISES.asciidoc} on the {@code film_text} table,
 * one method per exercise, named in each method's javadoc by the exercise's id ({@code SAKILA-FILM_TEXT-I-01},
 * ...). The exercises are context, not contract: a method that shows rows takes a {@link Pageable}, the page and the
 * sort, and returns a {@link Page}, so the order the exercise asks for is the caller's {@code PageRequest}. An
 * exercise that shows rows is a {@code default} method, {@code list...}, which builds a {@link Specification} with
 * the Criteria API and runs it through {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}. An
 * exercise that shows counts of two tables in one row cannot be a {@link Specification}, which only filters; such a
 * method, {@code select...}, declares its JPQL with {@link Query} and returns a projection, one of the interfaces
 * declared here, which Spring Data backs with the selected columns, each aliased with the name of the getter it
 * feeds. An exercise that searches with the full-text index is MySQL's own SQL ({@code MATCH ... AGAINST}), so such
 * a method, {@code count...}, declares it with {@link NativeQuery}; it runs on the Sakila database only.
 *
 * @see FilmText
 * @see FilmText_
 */
@Repository
public interface FilmTextRepository
        extends _Repository<FilmText, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * How many rows {@code film_text} and {@code film} have: a projection of {@link #selectRowCounts()}.
     */
    interface RowCounts {

        /**
         * Returns the number of rows of {@code film_text}.
         *
         * @return the number of rows of {@code film_text}.
         */
        long getFilmTexts();

        /**
         * Returns the number of rows of {@code film}.
         *
         * @return the number of rows of {@code film}.
         */
        long getFilms();
    }

    /**
     * Selects how many rows {@code film_text} and {@code film} have, in one row ({@code SAKILA-FILM_TEXT-I-01}; the
     * triggers keep the two the same).
     * <p>
     * One aggregate query over {@code film_text}, with the count of {@code film} as a scalar subquery in its select,
     * so it always has exactly one row, even over two empty tables. Two counts of two tables cannot be a
     * {@link Specification}, which filters one.
     *
     * @return the two counts.
     */
    @Query("""
            SELECT COUNT(ft) AS filmTexts, (SELECT COUNT(f) FROM Film f) AS films
            FROM FilmText ft""")
    RowCounts selectRowCounts();

    /**
     * Counts the rows whose title or description mention the specified word, by the full-text index
     * ({@code SAKILA-FILM_TEXT-I-02}, which asks for {@code drama}).
     * <p>
     * A {@link NativeQuery}: {@code MATCH ... AGAINST} is MySQL's own, so the method runs on the Sakila database
     * only, and has no slice test. In the default natural language mode, the search is case-insensitive, and a word
     * shorter than the index's minimum length, or a stopword, matches nothing.
     *
     * @param word the word to search for; must not be {@code null}.
     * @return the number of rows which mention {@code word}.
     */
    @NativeQuery("""
            SELECT COUNT(*)
            FROM film_text
            WHERE MATCH(title, description) AGAINST (:word)""")
    long countMatching(String word);

    /**
     * Lists the rows whose title differs from the title of their film ({@code SAKILA-FILM_TEXT-I-03}, which names
     * no order; the triggers keep the two the same, so the sample data has none).
     * <p>
     * A {@link Specification} of one inequality between the row's {@link FilmText_#TITLE} and the
     * {@link Film_#TITLE} of its {@link FilmText_#FILM}, which joins the {@code film} table. The comparison is the
     * database's, which on the Sakila database is case-insensitive, by the collation of the columns. The film is in
     * each row returned, as its {@link FilmText#getFilm()}, which the exercise shows next to the row's own title.
     *
     * @param pageable the page to return, and its sort; must not be {@code null}.
     * @return a page of the rows whose title is not their film's.
     */
    default Page<FilmText> listWithTitleDifferentFromFilm(final Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable is null");
        final Specification<FilmText> specification =
                (r, q, b) -> b.notEqual(r.get(FilmText_.title), r.get(FilmText_.film).get(Film_.title));
        return findAll(specification, pageable);
    }
}
