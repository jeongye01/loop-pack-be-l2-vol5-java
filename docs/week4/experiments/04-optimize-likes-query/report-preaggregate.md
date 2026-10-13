# 좋아요 선집계 후 상품과 조인

2026-10-14, `product_id` 인덱스를 유지하고 좋아요순 목록 쿼리만 변경했다. 별도 영구 집계 테이블은 만들지 않는다. 상품별 좋아요 개수를 다시 조회하는 20번의 SQL은 이번 변경에 포함하지 않았다.

```sql
SELECT p.* FROM product p
LEFT JOIN (
    SELECT product_id, COUNT(*) AS like_count
    FROM product_like
    GROUP BY product_id
) l ON l.product_id = p.id
WHERE p.deleted_at IS NULL
  AND (:brandId IS NULL OR p.brand_id = :brandId)
ORDER BY COALESCE(l.like_count, 0) DESC, p.id ASC;
```

페이지 크기·offset은 기존 Pageable로 적용한다. 삭제 상품 제외, 브랜드 필터, 좋아요 0개 포함, 동점 ID순, 페이지 경계 통합 테스트와 ArchUnit·Checkstyle을 통과했다.

## 단건 3회 결과

서울 앱에 `commerce-api:week4-preaggregate-20261014`로 배포했다(미커밋 코드 포함). `GET /api/v1/products?sort=likes_desc&page=0&size=20`, `X-USER-ID: 1`을 부하 없이 순차 호출했다. curl `time_total`, 제한 시간 30초, 캐시 초기화 없음. 재배포 후 첫 상품 요청을 포함한다.

| 요청 | HTTP | 시간(초) |
| --- | --- | ---: |
| 1 | 200 | 2.902847 |
| 2 | 200 | 2.416642 |
| 3 | 200 | 2.466446 |
| 평균 | — | **2.595312** |

세 응답 모두 기존 응답과 전체 JSON이 같았다. [인덱스만 추가한 이전 결과](report.md)의 평균 2.638316초와 비슷하다. 서로 다른 시점의 3회씩 비교이므로 **확실한 성능 개선으로 판단하지 않는다.**

## 실행 계획

브랜드 필터 없는 목록 SQL의 별도 `EXPLAIN ANALYZE` 결과다.

```text
-> Limit: 20 row(s) (actual time=2680..2680 rows=20 loops=1)
    -> Sort: coalesce(l.like_count,0) DESC, p.id (actual time=2680..2680 rows=20 loops=1)
        -> Stream results (actual time=443..2549 rows=1e+6 loops=1)
            -> Nested loop left join (actual time=443..2183 rows=1e+6 loops=1)
                -> Filter: p.deleted_at is null (actual time=0.114..507 rows=1e+6 loops=1)
                    -> Table scan on p (actual time=0.113..437 rows=1e+6 loops=1)
                -> Index lookup on l using <auto_key0> (product_id=p.id)
                   (actual time=0.00149..0.00153 rows=0.21 loops=1e+6)
                    -> Materialize (actual time=443..443 rows=210000 loops=1)
                        -> Group aggregate: count(0) (actual time=0.252..124 rows=210000 loops=1)
                            -> Covering index scan on product_like using idx_product_like_product_id
                               (actual time=0.248..79 rows=690000 loops=1)
```

좋아요 **69만 행 → 상품별 집계 21만 행**을 임시 결과로 만든다. 상품 100만 행마다 이 결과를 탐색하고 정렬한다. 조인 결과는 약 148만 행에서 100만 행으로 줄었지만, **상품 100만 행 읽기·100만 번 탐색·정렬은 남는다.** 목록 SQL은 이전 2.768초, 이번 2.680초로 비슷했다.
