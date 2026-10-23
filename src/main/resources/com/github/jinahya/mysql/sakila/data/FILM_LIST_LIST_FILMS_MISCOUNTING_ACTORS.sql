SELECT l.FID, l.title
FROM film_list l
WHERE l.actors IS NOT NULL
  AND LENGTH(l.actors) - LENGTH(REPLACE(l.actors, ',', '')) + 1
          <> (SELECT COUNT(*) FROM film_actor fa WHERE fa.film_id = l.FID)
ORDER BY l.FID
;
