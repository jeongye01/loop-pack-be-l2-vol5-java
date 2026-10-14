# 커밋별 일반 EXPLAIN 보완

2026-10-14 추가 수집. **당시 EC2의 누락된 원본을 복구한 것이 아니라, 커밋의 쿼리·인덱스와 seed로 로컬에서 재현한 출력**이다. 기존 EXPLAIN ANALYZE·응답 시간은 수정하지 않았다.

- MySQL 8.0.46, 로컬 `week4-perf-mysql-1`. 100만 상품은 별도 `perf_explain_history_v1`에 동일 CSV를 적재하고 모든 필드의 순서 고정 체크섬을 검증했다. [적재 검증](../seed-data/load-reports/perf_explain_history_v1.json).
- 첫 baseline만 기존 로컬 `perf_uniform_10k_v1` 1만 상품을 사용한다. EC2 DB와 기존 테이블·인덱스는 변경하지 않았다.
- `git show`로 각 커밋의 Repository·Adapter·엔티티를 읽었다. native SQL은 해당 커밋에서 추출했고, JPQL은 같은 조회 컬럼·필터·정렬의 물리 SQL로 옮겼다. 애플리케이션을 재배포해 JDBC SQL을 재수집한 것은 아니다.
- 기존 기본 인덱스에서 시작해 product_id → 좋아요 필터 → 최신순 → 가격순 인덱스를 시간 순으로 추가했다. 단계마다 `ANALYZE TABLE`을 수행하고 실제 DDL·인덱스·옵티마이저 설정·소스 해시를 저장했다.
- `EXPLAIN FORMAT=TRADITIONAL`의 `key`, `type`, `rows`, `filtered`, `Extra`를 그대로 보존한다. 예상 `rows`는 통계 표본에 따라 달라질 수 있다. 인덱스 후 예상 범위가 수십만 행이어도 LIMIT에 의해 실제 21행만 읽을 수 있으므로 실제 행 수는 기존 ANALYZE와 구분한다.
- 첫 페이지 기준으로 baseline은 20개, `last` 계약 이후는 21개 lookahead를 재현한다. 0개 좋아요·count SQL은 fallback 경로도 포함하며 첫 페이지에 모두 실행된다는 뜻은 아니다. 개별/일괄 보조 조회의 ID는 대표 사례다. EXPLAIN 자체는 요청당 반복 실행 횟수를 보여주지 않는다.
- 중간 미커밋 실험은 별도 커밋이 없으므로 최종 커밋 구성과 동일하다고 주장하지 않는다. 재측정 시간을 성능 개선 수치로 추가하지 않았다.

| 단계 | 코드 커밋 | 일반 EXPLAIN |
| --- | --- | --- |
| 10k baseline | `d80d218` | [출력](01-baseline/explain-traditional/d80d218-baseline.txt) · [SQL](01-baseline/explain-traditional/d80d218-baseline.sql) |
| 100만 상품 가격순 baseline | `c8d6468` | [출력](02-baseline/explain-traditional/c8d6468-baseline.txt) · [SQL](02-baseline/explain-traditional/c8d6468-baseline.sql) |
| 좋아요순 baseline | `e801069` | [출력](03-baseline-likes/explain-traditional/e801069-baseline.txt) · [SQL](03-baseline-likes/explain-traditional/e801069-baseline.sql) |
| 좋아요 product_id 인덱스 | `770c082` | [출력](04-optimize-likes-query/explain-traditional/770c082.txt) · [SQL](04-optimize-likes-query/explain-traditional/770c082.sql) |
| 좋아요 선집계 | `a4a5ec8` | [출력](04-optimize-likes-query/explain-traditional/a4a5ec8.txt) · [SQL](04-optimize-likes-query/explain-traditional/a4a5ec8.sql) |
| 좋아요 있는 상품 우선 | `3575b51` | [출력](04-optimize-likes-query/explain-traditional/3575b51.txt) · [SQL](04-optimize-likes-query/explain-traditional/3575b51.sql) |
| 페이지 선택 후 상세 조회 | `ce77577` | [출력](04-optimize-likes-query/explain-traditional/ce77577.txt) · [SQL](04-optimize-likes-query/explain-traditional/ce77577.sql) |
| 좋아요 순위 먼저 선택 | `1eed148` | [출력](04-optimize-likes-query/explain-traditional/1eed148.txt) · [SQL](04-optimize-likes-query/explain-traditional/1eed148.sql) |
| 전체 개수 제거·21개 lookahead | `01420df` | [출력](04-optimize-likes-query/explain-traditional/01420df.txt) · [SQL](04-optimize-likes-query/explain-traditional/01420df.sql) |
| N+1 일괄 조회·좋아요 수 재사용 | `d2ab232` | [출력](04-optimize-likes-query/explain-traditional/d2ab232.txt) · [SQL](04-optimize-likes-query/explain-traditional/d2ab232.sql) |
| 브랜드 먼저 필터 | `40a08b3` | [출력](04-optimize-likes-query/explain-traditional/40a08b3.txt) · [SQL](04-optimize-likes-query/explain-traditional/40a08b3.sql) |
| 최신순 인덱스 전 | `40a08b3` | [출력](05-optimize-latest-query/explain-traditional/before-40a08b3.txt) · [SQL](05-optimize-latest-query/explain-traditional/before-40a08b3.sql) |
| 최신순 인덱스 후 | `244d2e8` | [출력](05-optimize-latest-query/explain-traditional/after-244d2e8.txt) · [SQL](05-optimize-latest-query/explain-traditional/after-244d2e8.sql) |
| 가격순 인덱스 전 | `244d2e8` | [출력](06-optimize-price-query/explain-traditional/before-244d2e8.txt) · [SQL](06-optimize-price-query/explain-traditional/before-244d2e8.sql) |
| 가격순 인덱스 후 | `3bc0f0c` | [출력](06-optimize-price-query/explain-traditional/after-3bc0f0c.txt) · [SQL](06-optimize-price-query/explain-traditional/after-3bc0f0c.sql) |

총 15개 구성, 138개 EXPLAIN 문장. 출력과 같은 이름의 `.schema.txt`는 실제 스키마·인덱스·행 수, `.json`은 수집 시각·커밋·소스/seed 해시다.

재현 도구: [capture_historical_explain.py](support/capture_historical_explain.py). 기존 seed 적재기를 이용해 빈 전용 DB에 적재한 뒤 실행한다. 이미 인덱스가 추가된 DB로 전체 실행을 반복하면 거부한다. 현재 브랜치는 `volume-4`, HEAD는 `3bc0f0c`로 유지했다.
