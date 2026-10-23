SELECT n.category, n.length AS longest, COUNT(*) AS films
FROM nicer_but_slower_film_list n
WHERE n.length = (SELECT MAX(m.length) FROM nicer_but_slower_film_list m WHERE m.category = n.category)
GROUP BY n.category, n.length
ORDER BY n.category
;
