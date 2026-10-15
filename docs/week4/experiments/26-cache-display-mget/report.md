# 표시 캐시 MGET 비교 결과

커밋 `9857d98`. 최신순·브랜드 없음·첫 20개 통합 API, 캐시 ON. 목록 GET → 좋아요 수·재고 MGET으로 순차 3왕복을 2왕복으로 줄였다. 키·revision·TTL 300±30초·읽기 트랜잭션 범위는 유지했다.

각 조건 앱 재시작 → 300 RPS 20초 → 회복 → 1,700 RPS 60초 1회. VU 3,000·timeout 5초·별도 EC2 부하기·Trace 1%. 안정화되지 않은 짧은 예열 이후의 탐색 비교이므로 단일 변경의 인과 효과나 재현 가능한 개선 배율로 단정하지 않는다.

| 항목 | 직전 ON (GET 3회) | MGET 적용 |
| --- | ---: | ---: |
| 시작·완료 | 57244 | 55891 |
| 성공 | 57235 | 55870 |
| 성공률 | 99.9843% | 99.9624% |
| timeout | 9 | 21 |
| HTTP 오류 | 0 | 0 |
| 미시작 | 44757 | 46109 |
| 평균 ms | 3144.05 | 3222.53 |
| p99 ms | 4239 | 4352.10 |
| 실제 HTTP RPS (drain 포함) | 913.99 | 890.48 |

SLO(p99 ≤ 200ms, 성공률 100%, 미시작·미완료 0) 판정: **미달**. 목표 102,000건 대비 실제 전달량도 함께 판단했다. 이번 시작+미시작은 102,000건, 비교 기준은 102,001건으로 실행 경계의 실제 값을 그대로 보존했다. 미완료 0건·응답 불일치 0건. 수집·회복 완료: True.

**이번 단일 실행에서는 응답 성능 개선이 관측되지 않았다.** Redis 왕복 감소는 확인됐지만 앱 CPU·Tomcat·DB 커넥션 포화는 남았다. p99의 소폭 악화를 MGET의 인과 효과로 단정하지 않는다.

## 명령과 자원 근거

- 본 측정 전후 Redis 명령 증가: GET 55,893, MGET 55,893, SET 0, PSETEX 0. 사전/회복 점검도 포함된다. MGET은 명령 한 번으로 두 키를 읽는다.
- 표본 584건의 SQL 수: {'min': 2, 'max': 2, 'mean': 2, 'p99': 2}. revision 일괄 조회 개선은 유지됐다.
- 표본 커넥션 대기 평균 179.66ms. Hikari active 최대 40.0, pending 최대 161.0.
- 앱 CPU 평균 183.03% (1코어=100%, 앱 2코어), MySQL CPU 평균 49.53%.
- compilation time 누적 증가 65656ms. 이는 CPU 사용량 자체가 아니며 JVM 준비 비용이 결과에 섞였을 수 있다.
- Redis hit/miss/expired/evicted 증가: {'keyspace_hits': 167682, 'keyspace_misses': 0, 'expired_keys': 0, 'evicted_keys': 0}.
- 캐시 오류 카운터: 전 ['catalog_cache_errors_total{application="commerce-api",operation="get"} 72.0', 'catalog_cache_errors_total{application="commerce-api",operation="put"} 12.0'], 후 ['catalog_cache_errors_total{application="commerce-api",operation="get"} 72.0', 'catalog_cache_errors_total{application="commerce-api",operation="put"} 12.0'].

## 예열·검증

예열 6,001건 중 HTTP 오류 1건, timeout 0, p99 3,510ms. 서버 로그에서 Hikari 커넥션 획득 3초 timeout을 확인했다. 예열 안정화 성공으로 해석하지 않는다. 예열 전후 get 오류 +72, put 오류 +12도 관측됐으나 본 측정에는 추가 증가가 없었다. 해당 Redis 오류의 최초 원인은 이 측정만으로 확정하지 않는다.

백엔드 473개 테스트(기본469+비동기4), Checkstyle·ArchUnit 통과. 실제 Redis GET 1회·MGET 1회, 부분 miss/손상 시 해당 원본만 조회, 기존 hit TTL 미연장, 재적재 TTL 범위와 기존 정합성 회귀를 검증했다. 공통 실행기 테스트 11개 통과.

[원본 집계](slo-runs.json) · [자원 기록](results/20261015T031925-measure-1700-c69072/resource-observations.json) · [Redis 명령·TTL](results/20261015T031925-measure-1700-c69072/cache-state.json) · [배포](deployment.json) · [비교 기준](../25-revision-batch-cache-comparison/report.md).

종료 시 MGET 빌드·캐시 ON 유지. Redis를 기다리는 동안 DB 커넥션을 유지하는 트랜잭션 범위는 변경하지 않았으며 별도 분석/개선 후보로 남는다.
