package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.FilmText;
import com.github.jinahya.mysql.sakila.persistence.FilmText_;
import com.github.jinahya.mysql.sakila.persistence.Film_;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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
 * one method per exercise, in the order of the exercises' ids, each named in its javadoc by the id
 * ({@code SAKILA-FILM_TEXT-B-01}, ...) and by its role: the book's pick, this module's own extra, not a pick (kept from
 * before the picks), or native-only. The exercises are context, not contract. A Basic one is plain: no
 * {@link Pageable}, the order the exercise asks for written in the query, a {@link List} for the book's pick. An
 * Intermediate or Advanced one that shows rows takes a {@link Pageable}, the page and the sort, and returns a
 * {@link Page}, so the order the exercise asks for is the caller's {@code PageRequest}; one that counts them is
 * answered by the page of those rows, whose {@link Page#getTotalElements()} is the count. An exercise that shows
 * counts of two tables in one row cannot be a {@link Specification}, which only filters; such a method,
 * {@code select...}, declares its JPQL with {@link Query}. A result that is not an entity comes back as a
 * {@link jakarta.persistence.Tuple}, each column aliased in the select and read by that alias, with its type
 * ({@code tuple.get("films", Long.class)}); the aliases are also what a {@code Pageable} would sort by. Each method's
 * javadoc names its aliases.
 * <p>
 * Most exercises of this table search with the full-text index ({@code MATCH ... AGAINST}), or cut the description
 * with {@code SUBSTRING_INDEX}, or walk the titles with a recursive {@code WITH}, which is MySQL's own SQL, so such a
 * method declares it with {@link NativeQuery}, as the exercise has it; it runs on the Sakila database only. The
 * exercises upstream flags native-only return a {@link List} in the SQL's own order, or the one row of their counts;
 * a native query's columns are aliased with the names its tuple is read by, which is the one change made to the
 * exercise's SQL, and its numbers come back as the JDBC driver types them.
 *
 * @see FilmText
 * @see FilmText_
 */
@Repository
public interface FilmTextRepository
        extends _Repository<FilmText, Integer> {

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Finds the rows whose value of {@link FilmText_#DESCRIPTION} attribute contains both specified words
     * ({@code SAKILA-FILM_TEXT-B-01}, the book's pick, which asks for {@code Crocodile} and {@code Shark}, and orders
     * by {@link FilmText_#FILM_ID}).
     * <p>
     * The query, and its order, are derived from the method name: two {@code LIKE}s on the same attribute, each with
     * its word wrapped in {@code %}, which Spring Data adds and escapes, so the caller passes the word alone. The
     * comparison is the database's, which on the Sakila database is case-insensitive, by the collation of the
     * column; a word matches inside another, too.
     *
     * @param word1 one word; must not be {@code null}.
     * @param word2 the other word; must not be {@code null}.
     * @return a list of the rows whose description contains both {@code word1} and {@code word2}, by film id.
     */
    List<FilmText> findAllByDescriptionContainingAndDescriptionContainingOrderByFilmId(String word1, String word2);

    /**
     * Selects how many rows {@code film_text} and {@code film} have, in one row ({@code SAKILA-FILM_TEXT-I-01},
     * this module's own extra; the triggers keep the two the same).
     * <p>
     * One aggregate query over {@code film_text}, with the count of {@code film} as a scalar subquery in its select,
     * so it always has exactly one row, even over two empty tables. Two counts of two tables cannot be a
     * {@link Specification}, which filters one. The exercise gives one row, so the method takes no {@link Pageable}
     * and returns no {@link Page}: there is nothing to page or to sort.
     *
     * @return the two counts, as a tuple of {@code filmTexts} and {@code films} ({@link Long}).
     */
    @Query("""
            SELECT COUNT(ft) AS filmTexts, (SELECT COUNT(f) FROM Film f) AS films
            FROM FilmText ft""")
    Tuple selectRowCounts();

    /**
     * Lists the rows whose title or description mention the specified word, by the full-text index
     * ({@code SAKILA-FILM_TEXT-I-02}, the book's pick, which asks how many mention {@code drama}; that is the page's
     * {@link Page#getTotalElements()}, whatever its size, and the exercise names no order).
     * <p>
     * A {@link NativeQuery}: {@code MATCH ... AGAINST} is MySQL's own, so the method runs on the Sakila database
     * only, and has no slice test; it selects the rows, and its count query counts them, with the same
     * {@code MATCH}. In the default natural language mode, the search is case-insensitive, and a word shorter than
     * the index's minimum length, or a stopword, matches nothing. The sort of a native query is by column, such as
     * {@code film_id}, not by attribute.
     *
     * @param word     the word to search for; must not be {@code null}.
     * @param pageable the page to return, and its sort, by the columns of {@code film_text}; must not be
     *                 {@code null}.
     * @return a page of the rows which mention {@code word}.
     */
    @NativeQuery(value = """
            SELECT *
            FROM film_text
            WHERE MATCH(title, description) AGAINST (:word)""",
                 countQuery = """
                         SELECT COUNT(*)
                         FROM film_text
                         WHERE MATCH(title, description) AGAINST (:word)""")
    Page<FilmText> listMatching(String word, Pageable pageable);

    /**
     * Lists the rows whose title differs from the title of their film ({@code SAKILA-FILM_TEXT-I-03}, not a pick,
     * which names no order; the triggers keep the two the same, so the sample data has none).
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

    /**
     * Counts the rows which match the specified search in boolean mode, by the full-text index
     * ({@code SAKILA-FILM_TEXT-I-04}, native-only, which asks for a {@code boat} but not a {@code shark},
     * {@code +boat -shark}).
     * <p>
     * A {@link NativeQuery} with the exercise's SQL: {@code MATCH ... AGAINST ... IN BOOLEAN MODE} is MySQL's own, so
     * the method runs on the Sakila database only, and has no slice test. In boolean mode, a word prefixed with
     * {@code +} has to be there, and one prefixed with {@code -} must not.
     *
     * @param search the search, in the syntax of the boolean mode; must not be {@code null}.
     * @return the number of rows which match {@code search}.
     */
    @NativeQuery("""
            SELECT COUNT(*) AS films
            FROM film_text
            WHERE MATCH(title, description) AGAINST (:search IN BOOLEAN MODE)""")
    long countMatchingInBooleanMode(String search);

    /**
     * Selects how many rows mention either of two words, and how many mention both, by the full-text index, in one
     * row ({@code SAKILA-FILM_TEXT-I-05}, native-only, which asks for {@code crocodile} and {@code shark}).
     * <p>
     * A {@link NativeQuery} with the exercise's SQL: two counts, as scalar subqueries, one in the natural language
     * mode, which matches a row with either word, and one in boolean mode, which matches a row with both. The two
     * searches are the two words joined, {@code crocodile shark} and {@code +crocodile +shark}, which the SQL builds
     * with {@code CONCAT}. MySQL's own, so the method runs on the Sakila database only, and has no slice test.
     *
     * @param word1 one word; must not be {@code null}.
     * @param word2 the other word; must not be {@code null}.
     * @return the two counts, as a tuple of {@code eitherWord} and {@code bothWords}, each a number ({@link Long} on
     *         MySQL).
     */
    @NativeQuery("""
            SELECT (SELECT COUNT(*)
                    FROM film_text
                    WHERE MATCH(title, description) AGAINST (CONCAT(:word1, ' ', :word2))) AS eitherWord,
                   (SELECT COUNT(*)
                    FROM film_text
                    WHERE MATCH(title, description) AGAINST (CONCAT('+', :word1, ' +', :word2) IN BOOLEAN MODE))
                       AS bothWords""")
    Tuple selectEitherAndBothCounts(String word1, String word2);

    /**
     * Selects the most common kind of story in each category ({@code SAKILA-FILM_TEXT-A-01}, native-only, which
     * orders by the category's {@code name}; of kinds as common, the first by name).
     * <p>
     * A {@link NativeQuery} with the exercise's SQL: the kind is the third word of the description, cut with
     * {@code SUBSTRING_INDEX}, the films are counted by category and kind in a {@code WITH}, and
     * {@code ROW_NUMBER()} ranks the kinds of each category. Each select takes the whole row of what it reads, the
     * category's ({@code c.*}, grouped by its key) and the common table expressions' ({@code kinds.*},
     * {@code ranked.*}), with the computed columns beside it. MySQL's own, so the method runs on the Sakila database
     * only, and has no slice test.
     *
     * @return a list of the categories, with their most common kinds of story and the numbers of films of them, by
     *         category, each a tuple of the category's columns by their names ({@code category_id}, a number,
     *         {@code name} ({@link String}), {@code last_update}), {@code kind} ({@link String}), and {@code films} and
     *         {@code rn}, numbers ({@link Long} on MySQL).
     */
    @NativeQuery("""
            WITH kinds AS (SELECT c.*,
                                  SUBSTRING_INDEX(SUBSTRING_INDEX(ft.description, ' ', 3), ' ', -1) AS kind,
                                  COUNT(*) AS films
                           FROM film_text ft
                                    JOIN film_category fc ON fc.film_id = ft.film_id
                                    JOIN category c ON c.category_id = fc.category_id
                           GROUP BY c.category_id, kind),
                 ranked AS (SELECT kinds.*,
                                   ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY films DESC, kind) AS rn
                            FROM kinds)
            SELECT ranked.*
            FROM ranked
            WHERE rn = 1
            ORDER BY name""")
    List<Tuple> selectMostCommonKinds();

    /**
     * Selects the chain of titles from the specified film, each title starting with the last word of the one before
     * ({@code SAKILA-FILM_TEXT-A-02}, native-only, which starts from {@code ACADEMY DINOSAUR}, the film {@code 1},
     * goes ten steps, and orders by {@code step}).
     * <p>
     * A {@link NativeQuery} with the exercise's SQL: a recursive {@code WITH} joins each step's row to the rows whose
     * first word, cut with {@code SUBSTRING_INDEX}, is the step's last word. A step with more than one such row
     * branches the chain, so a step may have more than one row; the sample data has none from the film {@code 1}.
     * Each step selects the whole row of the {@code film_text} it reads ({@code ft.*}) beside its number. MySQL's
     * own, so the method runs on the Sakila database only, and has no slice test.
     *
     * @param filmId the id of the film to start from.
     * @param steps  the number of steps, the first one included.
     * @return a list of the steps of the chain, with their films, by step, each a tuple of {@code step}, a number, and
     *         the {@code film_text} row's columns by their names ({@code film_id}, a number, {@code title} and
     *         {@code description} ({@link String})).
     */
    @NativeQuery("""
            WITH RECURSIVE chain AS (SELECT 1 AS step, f.*
                                     FROM film_text f
                                     WHERE f.film_id = :filmId
                                     UNION ALL
                                     SELECT c.step + 1, ft.*
                                     FROM chain c
                                              JOIN film_text ft
                                                   ON SUBSTRING_INDEX(ft.title, ' ', 1)
                                                          = SUBSTRING_INDEX(c.title, ' ', -1)
                                     WHERE c.step < :steps)
            SELECT chain.*
            FROM chain
            ORDER BY step""")
    List<Tuple> selectTitleChain(int filmId, int steps);
}
