# 캐시 ON·revision 확인 — 동일 예열 절차 3회

사용자 요청으로 최초 재측정에 2회를 추가했다. 같은 721e264 JAR·GET+MGET·revision 일괄SQL·바깥 read-only 트랜잭션·TTL300±30초·인증을 유지했다. 매회 300→700 RPS 각 30초 예열 후 1,700 RPS 60초, VU3,000·timeout5초·별도 EC2·Trace1%·최신순 무브랜드 첫20개.

**1회차와 2회차는 설정 전환에 따른 앱 재시작 후 실행했고, 3회차는 2회차 종료/회복 후 재시작 없이 실행했다.** 예열 단계/시간은 같아도 JVM 준비 상태는 같지 않다.

| 회차 | 평균 | p95 | p99 | 성공 건수 | 미시작 | timeout | SLO |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 108.46ms | 422.00ms | **618.00ms** | 102,001 | 0 | 0 | 미달 |
| 2 | 308.44ms | 1123.00ms | **1326.00ms** | 102,001 | 0 | 0 | 미달 |
| 3 | 50.64ms | 259.00ms | **440.00ms** | 102,001 | 0 | 0 | 미달 |

SLO: p99≤200ms·성공률100%·미시작/미완료0. 각회 목표102,000건, 실행 경계의 실제 건수를 그대로 보존했다. p99를 평균 내지 않는다.

## 예열

| 회차 | 300 RPS 예열 p99 | 700 RPS 예열 p99 | 예열 실패 건수 |
| --- | ---: | ---: | ---: |
| 1 | 3610.00ms | 86.00ms | 0 |
| 2 | 3551.01ms | 137.00ms | 1 |
| 3 | 6.00ms | 9.00ms | 0 |

## 본 측정 자원·서버 표본

| 회차 | 표본 수 | SQL 수 범위 | 커넥션 대기 평균 | Hikari pending 최대 | 앱 CPU 평균 | compilation time 증가 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1072 | 2~2 | 48.729ms | 160 | 173.87% | 19912ms |
| 2 | 1009 | 2~2 | 44.528ms | 161 | 173.26% | 24255ms |
| 3 | 1018 | 2~2 | 28.077ms | 163 | 171.28% | 2522ms |

CPU는1코어=100%, 앱2코어 합산이다. compilation time은 누적 컴파일러 시간이며 CPU 사용량 자체가 아니다. 자원은 주기 표본이며 순간 최대를 보장하지 않는다.

본 측정 3회 모두 Redis 오류·miss·만료·eviction 증가가 없었고, HTTP 실패·응답 불일치·미완료도 0건이었다. 각 회차의 수집과 부하 종료 후 회복 확인을 통과했다.

재시작 없이 실행한 3회차는 예열 p99가 6ms/9ms였지만 본 부하에서는 440ms로 목표를 넘었다. 이때 Hikari active 최대 40개, pending 최대 163개, 앱 CPU 평균 171.28%(2코어)가 관측됐다. Redis 오류나 캐시 만료 없이도 DB 커넥션 대기와 앱 자원 압박이 남는다.

기존 수초 지연을 revision 단독 비용으로 확정하지 않는다. 재시작 없는 추가 실행과 신규 JVM 실행을 구분해 해석하고, 정상화된 상태에서 반복 성능을 확인해야 한다. 이번 고정 데이터 hit 중심 실험이 만료 스탬피드·혼합 쓰기·Redis 장애에서도 동일 SLO를 보장하는 것은 아니다.

[이번 3회 및 예열 원본](warmed-runs.json) · [전체 실행 원본](slo-runs.json) · [최초 재측정 상세](report-warmed-first.md) · [추가 실행 배포](deployment-repeats.txt) · [OFF 비교](../25-revision-batch-cache-comparison/report.md). 각 run 폴더의 resource-observations/cache-state/server.log에 근거를 보존했다.

측정 종료 후 원래 캐시 ON·revision 우회 실험 설정으로 복원했다. 동일 JAR 및 health 정상 확인: [복원 기록](restored-repeats.txt). 우회 설정은 고정 데이터 실험용이며 변경·삭제 정합성 보호를 검증하는 설정이 아니다.

## 코드 구현 기준 지연 원인 분석

아래는 위 3회 측정 당시 구현(721e264)에 대한 분석이다. 이후 hit/miss 트랜잭션 분리 변경은 마지막 절에 별도로 기록한다.

결론: 현재 병목의 핵심 후보는 revision 문자열 비교나 SELECT 자체보다 **매 hit마다 추가되는 DB 트랜잭션과 커넥션 점유 범위**다. revision 우회 실험은 SELECT와 바깥 트랜잭션을 함께 제거하므로, 결과 차이를 SELECT 하나의 비용이라고 부르면 부정확하다.

### 실제 hit 경로

```text
사용자 인증: DB 조회 → 인증 트랜잭션 종료·커넥션 반환
상품 조회: 읽기 트랜잭션 시작 → 커넥션 획득
  → catalog/stock revision을 SELECT 1회로 읽음
  → revision을 캐시 키에 붙임
  → Redis GET: 상품 목록 → JSON 역직렬화
  → Redis MGET: 좋아요 수·재고 → JSON 역직렬화
  → Map 생성·상품 20개 응답 DTO 조립
읽기 트랜잭션 종료 → 커넥션 반환
MVC에서 HTTP 응답 직렬화·전송
```

- [ProductReadFacade.page/pageWithinRead/compose](../../../../apps/commerce-api/src/main/java/com/loopers/product/interfaces/ProductReadFacade.java): `transaction.read`가 revision 조회뿐 아니라 Redis 조회와 응답 DTO 조립까지 감싼다. HTTP 응답 직렬화·전송 자체는 이 범위 밖이다.
- [CatalogReadTransaction.read](../../../../apps/commerce-api/src/main/java/com/loopers/support/cache/CatalogReadTransaction.java): 기본 경로는 read-only `TransactionTemplate.execute`를 사용한다. read-only라고 커넥션을 즉시 반환하는 것은 아니다. 우회 경로는 `operation.get()`으로 바깥 트랜잭션 자체를 생략한다.
- [CacheVersions.snapshot](../../../../apps/commerce-api/src/main/java/com/loopers/support/cache/CacheVersions.java): `JdbcTemplate`으로 `where namespace in (?, ?)`를 실행한다. 상품마다 반복하는 N+1이 아니라 요청당 1회 일괄 조회다. 이후 `Snapshot.key`는 조회된 revision을 문자열로 붙일 뿐 별도 DB 조회가 없다.
- [ResponseCache](../../../../apps/commerce-api/src/main/java/com/loopers/support/cache/ResponseCache.java), [CachedJson](../../../../apps/commerce-api/src/main/java/com/loopers/support/cache/CachedJson.java): 상품 GET 뒤 표시 데이터 MGET을 수행한다. 캐시가 모두 hit해도 이미 시작된 상품 조회 트랜잭션 안에서 수행된다.
- [jpa.yml](../../../../modules/jpa/src/main/resources/jpa.yml): Hikari 최대 커넥션 40개. 인증과 revision 조회가 같은 DataSource를 사용한다. 상품 조회가 커넥션을 오래 점유하면 다른 요청의 인증도 함께 기다릴 수 있다.

### 로그에서 확인한 비용

3회차 revision ON의 요청 표본 1,018건과 28번 우회 표본 1,054건을 비교했다. 아래 평균은 `server.log`의 `EXPERIMENT_JSON` 표본 평균이며, 전체 요청의 k6 p99와 다른 통계다. stage 값은 요청별 해당 호출들의 `totalMs` 합계다.

| 요청당 항목 | revision ON 3회차 | revision 우회 28번 |
| --- | ---: | ---: |
| SQL 실행 수 | 2회 | 1회 |
| 커넥션 획득 호출 수 | 2회 | 1회 |
| 커넥션 획득 소요 평균 | 28.08ms | 0.018ms |
| revision SELECT 평균 | 0.846ms | 없음 |
| 인증 SELECT 평균 | 0.881ms | 0.457ms |
| setAutoCommit 호출 수 / 합산 시간 평균 | 4회 / 2.264ms | 2회 / 0.563ms |
| setReadOnly 호출 수 / 합산 시간 평균 | 4회 / 2.938ms | 2회 / 0.769ms |
| commit 호출 수 / 합산 시간 평균 | 2회 / 1.729ms | 1회 / 0.418ms |
| close 호출 수 / 합산 시간 평균 | 2회 / 0.332ms | 1회 / 0.003ms |
| Hikari active / pending 관측 최대 | 40 / 163 | 10 / 0 |
| 앱 CPU 평균 (2코어, 최대 200%) | 171.28% | 135.41% |
| 전체 요청 p99 | 440ms | 52ms |

`connectionWaitMs`는 계측 코드의 `DataSource.getConnection()` 경과 시간 합계다. 인증·상품 조회 대기를 합친 값이므로 전부 revision 획득 대기라고 분리해서 말할 수는 없다. SQL 시간 역시 클라이언트 JDBC 실행 경과 시간이므로 DB 엔진 내부 CPU 시간과 같지 않다. JDBC 상태 변경/commit 호출 시간에는 드라이버·통신·스케줄링 지연이 포함되며 모든 호출이 각각 네트워크 왕복이라는 의미는 아니다.

원본: [revision ON 서버 로그](results/20261015T040059-measure-1700-ae82c2/server.log), [자원 관측](results/20261015T040059-measure-1700-ae82c2/resource-observations.json), [우회 서버 로그](../28-revision-bypass-warmed/results/20261015T033904-measure-1700-2dc5dd/server.log). 계측 위치: [ExperimentConfiguration](../../../../apps/commerce-api/src/main/java/com/loopers/config/experiment/ExperimentConfiguration.java).

### 부하에서 차이가 커지는 이유

1. 캐시 hit여도 요청당 인증 + revision으로 SELECT가 2회다. 1,700 RPS에서는 약 3,400 SELECT/s이며, 우회의 약 1,700 SELECT/s보다 많다. 트랜잭션 시작·종료와 JDBC 상태 설정도 추가된다.
2. revision SQL이 끝나도 Redis GET/MGET, JSON 역직렬화, DTO 조립 중 커넥션을 반환하지 않는 구조다. 이 작업의 CPU·네트워크 지연이 DB 커넥션 점유를 늘릴 수 있다.
3. 40개 커넥션이 차면 후속 요청이 기다린다. 3회차에도 active 40개, pending 163개, Tomcat busy 200개가 관측됐다. 대기 중인 요청이 늘어나 꼬리 지연이 커지는 경로와 일치한다.
4. 앱 CPU도 높아 처리·반환이 늦어질 수 있다. 그러므로 단순히 풀 크기를 늘리면 해결된다고 결론 내릴 수 없다.

본 측정에서는 Redis 오류·miss·만료 증가가 없었다. 따라서 이번 차이를 캐시 스탬피드나 Redis 오류에 따른 DB fallback으로 설명할 근거는 없다. `namespace`는 엔티티의 PK이며 조회 SQL에 `FOR UPDATE`가 없고 쓰기도 없는 실험이다. revision 행의 쓰기 락 경합이 원인이라는 증거도 없다.

### 확정 범위와 다음 분리 검증

코드상 추가 트랜잭션 범위와 로그의 추가 SQL·JDBC 호출·풀 대기는 확인했다. **커넥션을 실제로 몇 ms 점유했는지, Redis 대기·JSON 처리 각각이 얼마인지까지는 현재 계측으로 분리하지 못했다.** 위 경로가 병목을 증폭한다는 해석은 코드와 지표에 근거한 추론이며, p99 차이 전체를 revision SELECT 시간으로 환산하지 않는다. 두 실험의 JVM 상태·실행 시각도 완전히 동일하지 않다.

다음 비교는 revision 조회를 유지하면서 DB 커넥션 보유 범위를 줄인 경로를 별도로 만들어 같은 조건으로 측정하는 것이다. 단, 현재 바깥 트랜잭션은 revision과 cache miss 원본 조회에 같은 DB 스냅샷을 제공한다. 이를 단순히 제거하면 두 조회 사이의 변경과 늦은 채움에 대한 보장을 다시 검토해야 한다. 구현 변경 전에 revision 재확인·재시도 등 정합성 방식과 테스트를 정해야 하며, 이번 분석에서는 제품 코드나 정책을 변경하지 않았다.

## 후속 구현: hit/miss 트랜잭션 분리

- `CatalogReadTransaction.read`는 우선 트랜잭션 없이 전체 응답의 캐시 hit 여부를 확인한다. revision은 계속 DB에서 읽지만 JDBC 조회가 끝나면 커넥션을 반환한다.
- `ResponseCache.hitOnly` 안에서는 원본 loader를 실행하지 않는다. miss 또는 손상된 JSON을 만나면 내부 신호로 해당 시도를 중단한다. 상태는 `finally`에서 제거하므로 다음 요청에 남지 않는다.
- 전부 hit이면 Redis GET/MGET·JSON 변환·DTO 합성을 DB 트랜잭션 없이 완료한다.
- 하나라도 miss이면 기존 부분 hit 결과를 버리고, 읽기 트랜잭션 안에서 전체 작업을 다시 시작한다. revision부터 다시 읽으므로 그사이 상품이 변경되면 새 revision의 키와 데이터로 구성한다. revision이 같으면 나머지 hit은 계속 재사용하고 실제 miss 원본만 읽는다.
- miss 재실행은 기존과 같이 하나의 DB 스냅샷 안에서 revision·원본을 읽는다. miss 경로의 Redis 접근은 여전히 트랜잭션 안에 있다. 이번 변경 목표는 hit의 커넥션 점유 제거다.
- API·키 형식·TTL·삭제/소유권 계약은 유지한다. miss에는 캐시 확인과 revision 조회가 추가될 수 있으며, 성능 개선 폭은 아직 부하 재측정 전이다. 위 3회 결과는 변경 전 결과로 보존한다.

추가 검증 시나리오: 최신순·가격순·좋아요순·상세·내 좋아요 hit에서 Redis MGET 시 활성 DB 트랜잭션과 바인딩된 자원이 없는지, 전체 miss에서는 revision을 트랜잭션 안에서 다시 읽고 원본을 한 번만 조회하는지, 재고만 miss인 상태에서 상품 수정이 끼어들어도 옛 상품 hit과 새 revision을 섞지 않는지 확인한다. 기존 부분 miss·JSON 손상·늦은 채움·삭제·롤백 테스트도 그대로 유지한다.

검증: `:apps:commerce-api:check` 통과. 일반 회귀 480개(ArchUnit 포함), 별도 JVM 비동기 로깅 4개, 실패·오류·skip 0. Checkstyle main/test 및 `git diff --check` 통과. 추가한 HTTP 시나리오는 7개이며 배포·부하 재측정은 아직 수행하지 않았다.

후속 부하 측정 완료: [29번 결과](../29-cache-hit-miss-transaction/report.md). revision 확인 유지 상태에서 1,700 RPS 첫 1회 p99 124ms, 성공률 100%, 미시작 0. 기존 26번 수치는 변경 전 기록이다.
