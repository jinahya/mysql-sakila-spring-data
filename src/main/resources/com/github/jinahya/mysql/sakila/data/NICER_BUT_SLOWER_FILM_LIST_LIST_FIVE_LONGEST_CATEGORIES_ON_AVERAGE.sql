SELECT category, COUNT(*) AS films, ROUND(AVG(length), 2) AS average_length
FROM nicer_but_slower_film_list
GROUP BY category
ORDER BY average_length DESC, category
LIMIT 5
;
