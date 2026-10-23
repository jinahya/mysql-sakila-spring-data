SELECT CASE WHEN length < 60 THEN 'short' WHEN length <= 120 THEN 'medium' ELSE 'long' END AS band,
       COUNT(*) AS films, MIN(length) AS shortest, MAX(length) AS longest
FROM nicer_but_slower_film_list
GROUP BY band
ORDER BY shortest
;
