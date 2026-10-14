SELECT 'likes join; brand=NULL; fetch=20' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p LEFT JOIN product_like l ON l.product_id=p.id WHERE p.deleted_at IS NULL AND (NULL IS NULL OR p.brand_id=NULL) GROUP BY p.id ORDER BY COUNT(l.id) DESC,p.id ASC LIMIT 20 OFFSET 0;

SELECT 'likes join; brand=7; fetch=20' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p LEFT JOIN product_like l ON l.product_id=p.id WHERE p.deleted_at IS NULL AND (7 IS NULL OR p.brand_id=7) GROUP BY p.id ORDER BY COUNT(l.id) DESC,p.id ASC LIMIT 20 OFFSET 0;

SELECT 'total count; brand=NULL' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND (NULL IS NULL OR p.brand_id=NULL);

SELECT 'total count; brand=7' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND (7 IS NULL OR p.brand_id=7);

SELECT 'single like count; representative product 980807' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(id) FROM product_like WHERE product_id=980807;

SELECT 'single stock; representative product 980807' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id=980807;
