SELECT v.*, SUM(p.amount) AS taken_by_manager
FROM sales_by_store v
         JOIN staff s ON CONCAT(s.first_name, ' ', s.last_name) = v.manager
         JOIN payment p ON p.staff_id = s.staff_id
GROUP BY v.store, v.manager, v.total_sales
ORDER BY v.store
;
