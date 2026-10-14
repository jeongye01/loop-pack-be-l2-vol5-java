SELECT 'price; brand=NULL; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p WHERE p.deleted_at IS NULL AND (NULL IS NULL OR p.brand_id=NULL) ORDER BY p.price ASC,p.id ASC LIMIT 21 OFFSET 0;

SELECT 'price; brand=1; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p WHERE p.deleted_at IS NULL AND (1 IS NULL OR p.brand_id=1) ORDER BY p.price ASC,p.id ASC LIMIT 21 OFFSET 0;

SELECT 'price; brand=601; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p WHERE p.deleted_at IS NULL AND (601 IS NULL OR p.brand_id=601) ORDER BY p.price ASC,p.id ASC LIMIT 21 OFFSET 0;

SELECT 'single like count; representative product 980807' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(id) FROM product_like WHERE product_id=980807;

SELECT 'single stock; representative product 980807' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id=980807;

SELECT 'selected product detail; representative likes top20' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT p.id,p.active_name_key,p.brand_id,p.created_at,p.deleted_at,p.name,p.price,p.updated_at FROM product p WHERE p.id IN (980807,802385,624537,431374,277637,76056,2210,668912,653404,532964,678115,77319,13888,122697,152994,356828,535434,118696,623404,649753);

SELECT 'batch stock; representative likes top20' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id IN (980807,802385,624537,431374,277637,76056,2210,668912,653404,532964,678115,77319,13888,122697,152994,356828,535434,118696,623404,649753);

SELECT 'batch likes; latest/price path; representative IDs' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT product_id,COUNT(id) FROM product_like WHERE product_id IN (980807,802385,624537,431374,277637,76056,2210,668912,653404,532964,678115,77319,13888,122697,152994,356828,535434,118696,623404,649753) GROUP BY product_id;
