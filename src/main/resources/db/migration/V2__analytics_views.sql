-- Read-only analytics surface. The LLM is only ever shown these five views.
-- "Revenue" counts orders that were actually paid for (PENDING and CANCELLED excluded).

CREATE VIEW v_sales_by_day AS
SELECT o.created_at::date                       AS sale_date,
       SUM(oi.quantity * oi.unit_price)         AS revenue,
       COUNT(DISTINCT o.id)                     AS order_count,
       SUM(oi.quantity)                         AS units_sold,
       COUNT(DISTINCT o.user_id)                AS unique_customers
FROM orders o
         JOIN order_items oi ON oi.order_id = o.id
WHERE o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
GROUP BY o.created_at::date;

CREATE VIEW v_sales_by_category AS
SELECT o.created_at::date               AS sale_date,
       c.id                             AS category_id,
       c.name                           AS category_name,
       SUM(oi.quantity * oi.unit_price) AS revenue,
       SUM(oi.quantity)                 AS units_sold,
       COUNT(DISTINCT o.id)             AS order_count
FROM orders o
         JOIN order_items oi ON oi.order_id = o.id
         JOIN products p ON p.id = oi.product_id
         JOIN categories c ON c.id = p.category_id
WHERE o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
GROUP BY o.created_at::date, c.id, c.name;

CREATE VIEW v_product_sales_daily AS
SELECT o.created_at::date               AS sale_date,
       p.id                             AS product_id,
       p.name                           AS product_name,
       c.name                           AS category_name,
       SUM(oi.quantity * oi.unit_price) AS revenue,
       SUM(oi.quantity)                 AS units_sold,
       COUNT(DISTINCT o.id)             AS order_count
FROM orders o
         JOIN order_items oi ON oi.order_id = o.id
         JOIN products p ON p.id = oi.product_id
         JOIN categories c ON c.id = p.category_id
WHERE o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
GROUP BY o.created_at::date, p.id, p.name, c.name;

CREATE VIEW v_top_products AS
SELECT p.id                             AS product_id,
       p.name                           AS product_name,
       c.name                           AS category_name,
       p.price                          AS current_price,
       p.stock                          AS stock_remaining,
       SUM(oi.quantity * oi.unit_price) AS revenue,
       SUM(oi.quantity)                 AS units_sold,
       COUNT(DISTINCT o.id)             AS order_count
FROM products p
         JOIN categories c ON c.id = p.category_id
         LEFT JOIN order_items oi ON oi.product_id = p.id
         LEFT JOIN orders o ON o.id = oi.order_id AND o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
GROUP BY p.id, p.name, c.name, p.price, p.stock;

CREATE VIEW v_customer_summary AS
SELECT u.id                                        AS customer_id,
       u.email                                     AS email,
       u.display_name                              AS display_name,
       u.created_at::date                          AS signed_up_on,
       COUNT(DISTINCT o.id)                        AS order_count,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS lifetime_revenue,
       MAX(o.created_at)::date                     AS last_order_date
FROM users u
         LEFT JOIN orders o ON o.user_id = u.id AND o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
         LEFT JOIN order_items oi ON oi.order_id = o.id
WHERE u.role = 'CUSTOMER'
GROUP BY u.id, u.email, u.display_name, u.created_at;
