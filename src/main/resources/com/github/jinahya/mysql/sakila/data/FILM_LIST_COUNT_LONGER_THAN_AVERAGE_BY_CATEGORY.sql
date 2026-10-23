SELECT l.category, COUNT(*) AS longer_than_average
FROM film_list l
WHERE l.length > (SELECT AVG(o.length) FROM film_list o WHERE o.category = l.category)
GROUP BY l.category
ORDER BY longer_than_average DESC, l.category
;
