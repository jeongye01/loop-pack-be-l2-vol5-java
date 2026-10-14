SELECT 'CASE None';
EXPLAIN ANALYZE select p1_0.id,p1_0.active_name_key,p1_0.brand_id,p1_0.created_at,p1_0.deleted_at,p1_0.name,p1_0.price,p1_0.updated_at from product p1_0 where p1_0.deleted_at is null and (NULL is null or p1_0.brand_id=NULL) order by p1_0.price,p1_0.id limit 21;
SELECT 'CASE 1';
EXPLAIN ANALYZE select p1_0.id,p1_0.active_name_key,p1_0.brand_id,p1_0.created_at,p1_0.deleted_at,p1_0.name,p1_0.price,p1_0.updated_at from product p1_0 where p1_0.deleted_at is null and (1 is null or p1_0.brand_id=1) order by p1_0.price,p1_0.id limit 21;
SELECT 'CASE 601';
EXPLAIN ANALYZE select p1_0.id,p1_0.active_name_key,p1_0.brand_id,p1_0.created_at,p1_0.deleted_at,p1_0.name,p1_0.price,p1_0.updated_at from product p1_0 where p1_0.deleted_at is null and (601 is null or p1_0.brand_id=601) order by p1_0.price,p1_0.id limit 21;
