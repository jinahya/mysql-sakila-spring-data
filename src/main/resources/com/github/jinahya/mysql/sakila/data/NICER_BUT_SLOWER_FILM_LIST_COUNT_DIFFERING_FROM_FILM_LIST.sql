SELECT COUNT(*)
FROM nicer_but_slower_film_list n
         JOIN film_list l ON l.FID = n.FID AND l.category = n.category
WHERE NOT (n.title <=> l.title AND n.description <=> l.description AND n.price <=> l.price
           AND n.length <=> l.length AND n.rating <=> l.rating);
