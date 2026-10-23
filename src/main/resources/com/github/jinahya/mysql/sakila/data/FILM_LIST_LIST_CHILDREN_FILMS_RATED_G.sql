SELECT FID, title, length
FROM film_list
WHERE category = 'Children'
  AND rating = 'G'
ORDER BY title, FID
;
