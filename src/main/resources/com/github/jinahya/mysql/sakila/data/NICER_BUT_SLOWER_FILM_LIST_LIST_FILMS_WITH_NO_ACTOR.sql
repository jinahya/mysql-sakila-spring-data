SELECT n.*
FROM nicer_but_slower_film_list n
WHERE n.actors IS NULL
ORDER BY n.FID
;
