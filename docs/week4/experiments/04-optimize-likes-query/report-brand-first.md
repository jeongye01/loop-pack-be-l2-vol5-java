# 브랜드 상품부터 좋아요 집계

브랜드 필터가 있으면 해당 브랜드의 미삭제 상품을 먼저 읽고, 그 상품들의 좋아요만 집계한다. `STRAIGHT_JOIN`으로 상품 → 좋아요 탐색 순서를 지정하고 기존 브랜드 선두 인덱스와 좋아요 `product_id` 인덱스를 사용한다. 새 인덱스·캐시·저장 카운터는 추가하지 않았다.

## 단건 3회 비교

서울 EC2, `likes_desc`, `page=0`, `size=20`. 각 조건을 번갈아 순차 요청, 각각 워밍업 3회 후 측정 3회 평균. 프론트 제외. 변경 전과 배포 후는 측정 시점이 다르다.

| 조건 | 전체 응답 전 → 후 | 서버 처리 전 → 후 | 후보 SQL 전 → 후 |
| --- | ---: | ---: | ---: |
| 무필터 | 123.2 → 138.4ms | 110.7 → 126.8ms | 92.2 → 93.2ms |
| 브랜드 1 · 상품 5,000개 | 590.9 → 88.7ms | 566.9 → 72.6ms | 548.9 → 39.9ms |
| 브랜드 601 · 상품 333개 | 587.7 → 44.2ms | 566.4 → 30.6ms | 547.6 → 3.8ms |

**브랜드 1은 전체 응답 약 6.7배, 브랜드 601은 약 13.3배 빨라졌다.** 브랜드 필터의 SQL은 6회로 동일하다. 반복 호출 수가 아니라 각 SQL에서 읽고 집계하는 데이터가 줄었다.

무필터 SQL은 변경하지 않았고 후보 SQL은 약 92 → 93ms로 비슷했다. 배포 후 무필터의 앱 처리·전체 응답 시간은 이번 관측에서 늘었으므로 같은 폭의 개선을 주장하지 않는다.

## 읽은 데이터

별도 `EXPLAIN ANALYZE`에서 확인한 실제 행 수다. 아래 실행계획 시간은 API 측정과 별개다.

| 조건 | 브랜드 인덱스로 읽은 상품 | 좋아요 탐색 횟수 | 읽은 좋아요 | 집계 후 양수 상품 |
| --- | ---: | ---: | ---: | ---: |
| 브랜드 1 | 5,000행 | 5,000회 | 3,492행 | 1,036개 |
| 브랜드 601 | 333행 | 333회 | 180행 | 62개 |

```text
이전: 전체 좋아요 집계 → 상품 조인·브랜드 확인 → 정렬 → 21개
이후: 해당 브랜드 상품 → 각 상품의 좋아요 인덱스 탐색 → 집계·정렬 → 21개
```

브랜드 1 실행계획 핵심:

```text
Index lookup on candidate using uk_product_active_name (brand_id=1)
  actual rows=5000 loops=1
Covering index lookup on l using idx_product_like_product_id (product_id=candidate.id)
  actual rows=0.698 loops=5000
Nested loop inner join: rows=3492
Aggregate using temporary table: rows=1036
Limit: 21 rows
```

상품 20개와 다음 페이지 여부 확인용 1개를 고른다. 좋아요 0개 구간으로 넘어갈 때 필요한 양수 상품 수도 전체 집계 대신 해당 브랜드 상품의 `EXISTS`로 계산한다. 0개 상품을 채우는 기존 경로와 무필터 경로는 유지했다.

모든 측정 응답이 이전과 전체 필드·순서·`last`까지 동일했다. 기존 삭제·브랜드·0개·동점·페이지 경계 테스트를 포함해 HTTP·통합·계측·ArchUnit 229개와 Checkstyle 통과. 브랜드가 전체 상품 대부분을 차지하는 데이터에서는 이 관측과 다른 결과가 나올 수 있다.

배포 이미지: `commerce-api:week4-brand-first-20261014`. JAR SHA-256: `8abfbdb4be560a8218529fdd3f7205c3fa456c23f56366c05915a5a982aab174`.
원본: `build/04-optimize-likes-query/brand-filter/`, `brand-first/`. Run: `brands-1791924251` → `brandfirst-1791940723`. 실행계획: `brand-first/explain.sql`, `explain.txt`.

## 일반 EXPLAIN

[출력 원본](explain-traditional/40a08b3.txt) · [실행 SQL](explain-traditional/40a08b3.sql) · [당시 구성 재현 정보](explain-traditional/40a08b3.json).

해당 커밋의 쿼리·인덱스를 별도 로컬 MySQL 8.0.46에서 재현해 추가 수집했다. 기존 EC2 측정 당시 출력과는 구분한다. `rows`는 추정치이며 실제 읽은 행 수·loops·시간은 기존 `EXPLAIN ANALYZE`를 참고한다. [재현 범위](../historical-explain.md).
