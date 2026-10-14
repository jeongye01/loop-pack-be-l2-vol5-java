SELECT 'price; brand=7; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p WHERE p.deleted_at IS NULL AND (7 IS NULL OR p.brand_id=7) ORDER BY p.price ASC,p.id ASC LIMIT 20 OFFSET 0;

SELECT 'total count; brand=7' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND (7 IS NULL OR p.brand_id=7);

SELECT 'single like count; representative product 1' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(id) FROM product_like WHERE product_id=1;

SELECT 'single stock; representative product 1' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id=1;
