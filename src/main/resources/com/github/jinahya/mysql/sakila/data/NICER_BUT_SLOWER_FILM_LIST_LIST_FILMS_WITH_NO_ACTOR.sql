SELECT FID, title, category
FROM nicer_but_slower_film_list
WHERE actors IS NULL
ORDER BY FID
;
