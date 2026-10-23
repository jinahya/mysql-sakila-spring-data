SELECT (SELECT SUM(total_sales) FROM sales_by_store)                 AS by_store,
       (SELECT SUM(amount) FROM payment WHERE rental_id IS NOT NULL) AS with_a_rental
;
