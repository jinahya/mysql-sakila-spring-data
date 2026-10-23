SELECT COUNT(*) AS mismatched
FROM nicer_but_slower_film_list n
WHERE n.actors IS NOT NULL
  AND CHAR_LENGTH(n.actors) - CHAR_LENGTH(REPLACE(n.actors, ',', '')) + 1
      <> (SELECT COUNT(*) FROM film_actor fa WHERE fa.film_id = n.FID)
;
