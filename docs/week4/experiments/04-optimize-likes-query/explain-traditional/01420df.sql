SELECT 'findRankedCandidates; brand=NULL; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select ranked.product_id as productId, p.id as eligibleProductId
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
EXPLAIN FORMAT=TRADITIONAL select p.* from (
            select candidate.id, l.like_count
            from (
                select product_id, count(*) as like_count
                from product_like
                group by product_id
            ) l join product candidate force index (idx_product_likes_filter) on candidate.id = l.product_id
            where candidate.deleted_at is null
              and (1 is null or candidate.brand_id = 1)
            order by l.like_count desc, candidate.id asc
            limit 21 offset 0
        ) page join product p on p.id = page.id
        order by page.like_count desc, page.id asc;

SELECT 'countLikedCustomerProducts; brand=1; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from (
            select product_id from product_like group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (1 is null or p.brand_id = 1);

SELECT 'findUnlikedCustomerProducts; brand=1; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (1 is null or p.brand_id = 1)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 21 offset 0;

SELECT 'findLikedCustomerProducts; brand=601; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from (
            select candidate.id, l.like_count
            from (
                select product_id, count(*) as like_count
                from product_like
                group by product_id
            ) l join product candidate force index (idx_product_likes_filter) on candidate.id = l.product_id
            where candidate.deleted_at is null
              and (601 is null or candidate.brand_id = 601)
            order by l.like_count desc, candidate.id asc
            limit 21 offset 0
        ) page join product p on p.id = page.id
        order by page.like_count desc, page.id asc;

SELECT 'countLikedCustomerProducts; brand=601; fetch=21; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from (
            select product_id from product_like group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (601 is null or p.brand_id = 601);

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
