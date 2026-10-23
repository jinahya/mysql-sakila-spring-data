SELECT (SELECT COUNT(*) FROM film_list)                    AS film_list,
       (SELECT COUNT(DISTINCT film_id) FROM film_category) AS films_with_a_category
;
