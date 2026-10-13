-- Read-only experiment; cursor anchors and positive segment size are prepared outside timing.
-- SET profiling=1; execute each query; SHOW PROFILES;

-- offset-1
SELECT ranked.product_id,p.id,ranked.like_count FROM (
 SELECT product_id,COUNT(*) like_count FROM product_like GROUP BY product_id
 ORDER BY like_count DESC,product_id ASC LIMIT 21 OFFSET 0
 ) ranked LEFT JOIN product p FORCE INDEX(idx_product_likes_filter)
 ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

-- cursor-1
SELECT ranked.product_id,p.id,ranked.like_count FROM (
 SELECT product_id,COUNT(*) like_count FROM product_like GROUP BY product_id
 ORDER BY like_count DESC,product_id ASC LIMIT 21 OFFSET 0
 ) ranked LEFT JOIN product p FORCE INDEX(idx_product_likes_filter)
 ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

-- offset-10
SELECT ranked.product_id,p.id,ranked.like_count FROM (
 SELECT product_id,COUNT(*) like_count FROM product_like GROUP BY product_id
 ORDER BY like_count DESC,product_id ASC LIMIT 21 OFFSET 180
 ) ranked LEFT JOIN product p FORCE INDEX(idx_product_likes_filter)
 ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

-- cursor-10
SELECT ranked.product_id,p.id,ranked.like_count FROM (
 SELECT product_id,COUNT(*) like_count FROM product_like GROUP BY product_id HAVING COUNT(*) < 72 OR (COUNT(*) = 72 AND product_id > 96547)
 ORDER BY like_count DESC,product_id ASC LIMIT 21 OFFSET 0
 ) ranked LEFT JOIN product p FORCE INDEX(idx_product_likes_filter)
 ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

-- offset-100
SELECT ranked.product_id,p.id,ranked.like_count FROM (
 SELECT product_id,COUNT(*) like_count FROM product_like GROUP BY product_id
 ORDER BY like_count DESC,product_id ASC LIMIT 21 OFFSET 1980
 ) ranked LEFT JOIN product p FORCE INDEX(idx_product_likes_filter)
 ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

-- cursor-100
SELECT ranked.product_id,p.id,ranked.like_count FROM (
 SELECT product_id,COUNT(*) like_count FROM product_like GROUP BY product_id HAVING COUNT(*) < 28 OR (COUNT(*) = 28 AND product_id > 114354)
 ORDER BY like_count DESC,product_id ASC LIMIT 21 OFFSET 0
 ) ranked LEFT JOIN product p FORCE INDEX(idx_product_likes_filter)
 ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

-- offset-50000
SELECT p.id,p.id,0 FROM product p WHERE p.deleted_at IS NULL

 AND NOT EXISTS(SELECT 1 FROM product_like l WHERE l.product_id=p.id)
 ORDER BY p.id LIMIT 21 OFFSET 789980;

-- cursor-50000
SELECT p.id,p.id,0 FROM product p WHERE p.deleted_at IS NULL
 AND p.id > 999975
 AND NOT EXISTS(SELECT 1 FROM product_like l WHERE l.product_id=p.id)
 ORDER BY p.id LIMIT 21 OFFSET 0;
