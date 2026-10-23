SELECT l.*
FROM film_list l
WHERE l.actors IS NULL
ORDER BY l.FID
;
