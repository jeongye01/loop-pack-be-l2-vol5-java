SELECT 'findLikedCustomerProducts; brand=NULL; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from (
            select product_id, count(*) as like_count
            from product_like
            group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (NULL is null or p.brand_id = NULL)
        order by l.like_count desc, p.id asc LIMIT 20 OFFSET 0;

SELECT 'countLikedCustomerProducts; brand=NULL; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from (
            select product_id from product_like group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (NULL is null or p.brand_id = NULL);

SELECT 'findUnlikedCustomerProducts; brand=NULL; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (NULL is null or p.brand_id = NULL)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 20 offset 0;

SELECT 'findLikedCustomerProducts; brand=1; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from (
            select product_id, count(*) as like_count
            from product_like
            group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (1 is null or p.brand_id = 1)
        order by l.like_count desc, p.id asc LIMIT 20 OFFSET 0;

SELECT 'countLikedCustomerProducts; brand=1; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from (
            select product_id from product_like group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (1 is null or p.brand_id = 1);

SELECT 'findUnlikedCustomerProducts; brand=1; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (1 is null or p.brand_id = 1)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 20 offset 0;

SELECT 'findLikedCustomerProducts; brand=601; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from (
            select product_id, count(*) as like_count
            from product_like
            group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (601 is null or p.brand_id = 601)
        order by l.like_count desc, p.id asc LIMIT 20 OFFSET 0;

SELECT 'countLikedCustomerProducts; brand=601; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select count(*) from (
            select product_id from product_like group by product_id
        ) l join product p on p.id = l.product_id
        where p.deleted_at is null
          and (601 is null or p.brand_id = 601);

SELECT 'findUnlikedCustomerProducts; brand=601; fetch=20; offset=0' AS query_case;
EXPLAIN FORMAT=TRADITIONAL select p.* from product p
        where p.deleted_at is null
          and (601 is null or p.brand_id = 601)
          and not exists (select 1 from product_like l where l.product_id = p.id)
        order by p.id asc
        limit 20 offset 0;

SELECT 'total count; brand=NULL' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND (NULL IS NULL OR p.brand_id=NULL);

SELECT 'total count; brand=1' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND (1 IS NULL OR p.brand_id=1);

SELECT 'total count; brand=601' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(p.id) FROM product p WHERE p.deleted_at IS NULL AND (601 IS NULL OR p.brand_id=601);

SELECT 'single like count; representative product 980807' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT COUNT(id) FROM product_like WHERE product_id=980807;

SELECT 'single stock; representative product 980807' AS query_case;
EXPLAIN FORMAT=TRADITIONAL SELECT s.id,s.created_at,s.deleted_at,s.product_id,s.quantity,s.updated_at FROM stock s WHERE s.product_id=980807;
