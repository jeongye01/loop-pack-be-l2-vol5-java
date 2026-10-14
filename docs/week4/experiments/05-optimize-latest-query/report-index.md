# 최신순 복합 인덱스 적용

상품 테이블에 무필터용 `(deleted_at, created_at DESC, id ASC)`, 브랜드용 `(brand_id, deleted_at, created_at DESC, id ASC)` 인덱스를 추가했다. 쿼리·응답 계약은 그대로다. [적용 SQL](add-latest-indexes.sql)

## 단건 3회 비교

동일한 서울 EC2 앱에서 재배포 없이 DB 인덱스만 추가했다. 각 조건 워밍업 3회 후 순차 3회 평균, `latest`, `page=0`, `size=20`, 프론트 제외.

| 조건 | 전체 응답 전 → 후 | 서버 처리 전 → 후 | 목록 SQL 전 → 후 |
| --- | ---: | ---: | ---: |
| 무필터 | 3988.4 → 32.1ms | 3942.6 → 19.2ms | 3920.0 → 1.2ms |
| 브랜드 1 | 168.1 → 27.6ms | 155.1 → 16.1ms | 132.9 → 2.5ms |
| 브랜드 601 | 42.3 → 25.7ms | 29.9 → 14.5ms | 3.5 → 0.8ms |

**무필터 전체 응답 약 124배 개선.** 세 조건의 모든 측정 응답은 적용 전과 전체 필드·순서·`last`가 동일했다. 단건 3회 관측이며 부하 한계나 p95 결과는 아니다.

## 실행계획

| 조건 | 이전에 읽은 상품 | 이후에 읽은 상품 | 이후 별도 SQL 실행계획 시간 |
| --- | ---: | ---: | ---: |
| 무필터 | 1,000,000행 | 21행 | 0.576ms |
| 브랜드 1 | 5,000행 | 21행 | 0.729ms |
| 브랜드 601 | 333행 | 21행 | 0.192ms |

```text
CASE None
-> Limit: 21 row(s)  (cost=61620 rows=21) (actual time=0.569..0.576 rows=21 loops=1)
    -> Index lookup on p1_0 using idx_product_latest (deleted_at=NULL), with index condition: (p1_0.deleted_at is null)  (cost=61620 rows=497004) (actual time=0.569..0.574 rows=21 loops=1)

CASE 1
-> Limit: 21 row(s)  (cost=3886 rows=21) (actual time=0.721..0.729 rows=21 loops=1)
    -> Index lookup on p1_0 using idx_product_brand_latest (brand_id=1, deleted_at=NULL), with index condition: (p1_0.deleted_at is null)  (cost=3886 rows=5000) (actual time=0.721..0.726 rows=21 loops=1)

CASE 601
-> Limit: 21 row(s)  (cost=511 rows=21) (actual time=0.185..0.192 rows=21 loops=1)
    -> Index range scan on p1_0 using idx_product_brand_latest over (brand_id = 601 AND deleted_at = NULL), with index condition: ((p1_0.deleted_at is null) and (p1_0.brand_id = 601))  (cost=511 rows=333) (actual time=0.184..0.19 rows=21 loops=1)
```

추가 Sort가 없어지고 인덱스 순서로 21행을 읽는다. 이 인덱스는 상품명·가격 등 전체 응답 컬럼을 담지 않으므로 목록 쿼리의 covering index는 아니다. 선택한 상품 행을 읽는 작업은 남지만 대상이 21개로 제한된다.

JPA에도 두 인덱스 선언을 반영했고, 컬럼 순서와 `created_at DESC` 방향을 검증하는 테스트를 추가했다. 기존 경계·HTTP·통합·계측·ArchUnit 포함 230개 테스트 및 Checkstyle 통과. 기존 EC2는 `ddl-auto`가 꺼져 있어 SQL로 추가했다. 앱 이미지는 `commerce-api:week4-brand-first-20261014` 유지.

원본: `build/05-optimize-latest-query/after/`. Run `latestindex-1791942711`. [적용 전](report.md) · [디스크 기록](storage.md).

## 데이터 읽기 흐름

![최신순 인덱스 적용 후](assets/after-read-flow.svg)

## 일반 EXPLAIN

[출력 원본](explain-traditional/after-244d2e8.txt) · [실행 SQL](explain-traditional/after-244d2e8.sql) · [당시 구성 재현 정보](explain-traditional/after-244d2e8.json).

해당 커밋의 쿼리·인덱스를 별도 로컬 MySQL 8.0.46에서 재현해 추가 수집했다. 기존 EC2 측정 당시 출력과는 구분한다. `rows`는 추정치이며 실제 읽은 행 수·loops·시간은 기존 `EXPLAIN ANALYZE`를 참고한다. [재현 범위](../historical-explain.md).
