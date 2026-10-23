SELECT rating,
       SUM(CASE WHEN price = 0.99 THEN 1 ELSE 0 END) AS price_0_99,
       SUM(CASE WHEN price = 2.99 THEN 1 ELSE 0 END) AS price_2_99,
       SUM(CASE WHEN price = 4.99 THEN 1 ELSE 0 END) AS price_4_99
FROM film_list
GROUP BY rating
ORDER BY rating
;
