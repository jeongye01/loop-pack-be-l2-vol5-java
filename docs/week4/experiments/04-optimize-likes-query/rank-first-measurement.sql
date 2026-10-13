-- 무필터 첫 페이지 측정 SQL. 두 번째 SQL의 ID는 첫 번째 결과에서 얻은 실제 20개다.
-- SET profiling=1 이후 두 SQL을 순차 3회 실행하고 SHOW PROFILES로 Duration(초)을 확인했다.
SELECT ranked.product_id AS productId,p.id AS eligibleProductId FROM (SELECT product_id,COUNT(*) AS like_count FROM product_like GROUP BY product_id ORDER BY like_count DESC,product_id ASC LIMIT 20 OFFSET 0) ranked LEFT JOIN product p FORCE INDEX (idx_product_likes_filter) ON p.id=ranked.product_id AND p.deleted_at IS NULL ORDER BY ranked.like_count DESC,ranked.product_id ASC;

SELECT * FROM product WHERE id IN (980807,802385,624537,431374,277637,76056,2210,668912,653404,532964,678115,77319,13888,122697,152994,356828,535434,118696,623404,649753);
