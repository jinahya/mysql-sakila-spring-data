SELECT l.*
FROM film_list l
WHERE l.category = 'Children'
  AND l.rating = 'G'
ORDER BY l.title, l.FID
;
