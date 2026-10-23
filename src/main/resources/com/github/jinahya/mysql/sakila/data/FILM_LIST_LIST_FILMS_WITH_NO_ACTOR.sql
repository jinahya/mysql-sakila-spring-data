SELECT FID, title, category
FROM film_list
WHERE actors IS NULL
ORDER BY FID
;
