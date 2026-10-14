SELECT 'findRankedCandidates; brand=NULL; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select ranked.product_id as productId, p.id as eligibleProductId, ranked.like_count as likeCount
        from (
            select product_id, count(*) as like_count
            from product_like
            group by product_id
            order by like_count desc, product_id asc
            limit 21 offset 0
        ) ranked
        left join product p force index (idx_product_likes_filter)
          on p.id = ranked.product_id and p.deleted_at is null
        order by ranked.like_count desc, ranked.product_id asc;

SELECT 'findUnlikedCustomerProducts; brand=NULL; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (NULL is null or p.brand_id = NULL)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 21 offset 0;

SELECT 'findLikedCustomerProducts; brand=1; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select page.id as productId, page.id as eligibleProductId, page.like_count as likeCount from (
            select candidate.id, count(*) as like_count
            from product candidate force index (uk_product_active_name)
            straight_join product_like l force index (idx_product_like_product_id)
              on l.product_id = candidate.id
            where candidate.brand_id = 1 and candidate.deleted_at is null
            group by candidate.id
            order by like_count desc, candidate.id asc
            limit 21 offset 0
        ) page
        order by page.like_count desc, page.id asc;

SELECT 'countLikedCustomerProducts; brand=1; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from product p force index (uk_product_active_name)
        where p.brand_id = 1 and p.deleted_at is null
          and exists (select 1 from product_like l where l.product_id = p.id);

SELECT 'findUnlikedCustomerProducts; brand=1; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (1 is null or p.brand_id = 1)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 21 offset 0;

SELECT 'findLikedCustomerProducts; brand=601; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select page.id as productId, page.id as eligibleProductId, page.like_count as likeCount from (
            select candidate.id, count(*) as like_count
            from product candidate force index (uk_product_active_name)
            straight_join product_like l force index (idx_product_like_product_id)
              on l.product_id = candidate.id
            where candidate.brand_id = 601 and candidate.deleted_at is null
            group by candidate.id
            order by like_count desc, candidate.id asc
            limit 21 offset 0
        ) page
        order by page.like_count desc, page.id asc;

SELECT 'countLikedCustomerProducts; brand=601; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from product p force index (uk_product_active_name)
        where p.brand_id = 601 and p.deleted_at is null
          and exists (select 1 from product_like l where l.product_id = p.id);

SELECT 'findUnlikedCustomerProducts; brand=601; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (601 is null or p.brand_id = 601)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 21 offset 0;

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
