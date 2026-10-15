# hit/miss 트랜잭션 분리 — revision 유지 상태의 첫 부하 측정

**1,700 RPS·60초·VU 3,000에서 p99 124ms, 평균 11.66ms. 102,000건 전부 성공, 미시작·timeout·응답 불일치·미완료 0건으로 이번 1회 SLO 통과.**

캐시 ON, DB revision 확인 유지(우회 OFF). 전체 hit의 바깥 트랜잭션을 제거했고 miss는 revision부터 트랜잭션 안에서 재조회한다. 최신순·무브랜드·page=0·size=20 통합 API, 동일 seed, 별도 EC2 부하기, TTL 300±30초, GET+MGET, 상세 Trace 1%를 유지했다. API 응답 시간은 전체 요청에서 측정했다.

배포하면서 앱을 재시작했다. 300→700 RPS 각 30초 예열과 회복 확인 후 본 측정 1회. 이번은 정상 hit 기준선이며 스탬피드 재현은 아니다.

## 예열과 본 측정

| 단계 | 시작·완료 | 성공률 | 미시작 | timeout | HTTP 오류 | 평균 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| warmup 300 RPS | 9,001 | 32.71% | 0 | 6,053 | 4 | 4489.06ms | 5001ms |
| warmup 700 RPS | 21,001 | 100.00% | 0 | 0 | 0 | 5.75ms | 45ms |
| measure 1700 RPS | 102,000 | 100.00% | 0 | 0 | 0 | 11.66ms | 124ms |

재시작 직후 첫 예열은 실패했다. timeout 6,053건과 HTTP 오류 4건을 제외하거나 성공으로 처리하지 않았다. 기존 실행 옵션에 따라 수집·서버 회복 확인 후 진행했고, 700 RPS 예열은 정상화됐다. 따라서 최초 기동부터 SLO를 지켰다는 결론이 아니다.

본 측정 p50 **3ms**, p95 **50ms**, p99 **124ms**, 최대 **336ms**. 실제 처리량(drain 포함) **1699.85 RPS**. 수집 항목 전부 완료, 부하 종료 후 서버 회복 확인. SLO는 p99 ≤ 200ms·성공률 100%·미시작/미완료/불일치 0으로 판정했다.

## 변경 전과 비교

| 항목 | 변경 전 revision ON 3회차 | 변경 후 이번 1회 |
| --- | ---: | ---: |
| p99 | 440ms | 124ms |
| 평균 응답 | 50.64ms | 11.66ms |
| 요청당 SQL (서버 표본) | 2회 | 2회 |
| 커넥션 획득 소요 평균 (서버 표본) | 28.08ms | 0.154ms |
| Hikari active 최대 | 40 | 26 |
| Hikari pending 최대 | 163 | 0 |
| Tomcat busy 최대 | 200 | 58 |
| 앱 CPU 평균 (2코어, 최대 200%) | 171.28% | 148.10% |
| MySQL CPU 평균 (1코어=100%) | 97.30% | 62.97% |

변경 전 3회 p99는 **618 / 1,326 / 440ms**, 변경 후 첫 결과는 **124ms**다. 위 비교 대상인 변경 전 3회차는 재시작 없이 실행했고 이번은 새 JVM에서 시작했다. 예열 단계는 같아도 JVM·실행 시점이 같지 않으므로 정확한 인과 배율이나 반복 재현성을 주장하지 않는다.

## 코드 변경이 실제로 적용됐는지

서버 상세 표본 **1,035건 전부 SQL 2회**로, 인증 SELECT와 `read_cache_revision` SELECT가 모두 남았다. revision SELECT 표본 평균은 **0.648ms**, 인증 SELECT는 **0.684ms**다. DB revision을 생략해서 나온 결과가 아니다.

| 요청당 JDBC 호출 (서버 표본 전체에서 동일) | 변경 전 3회차 | 변경 후 |
| --- | ---: | ---: |
| acquire | 2 | 2 |
| commit | 2 | 1 |
| setAutoCommit | 4 | 2 |
| setReadOnly | 4 | 2 |

인증 트랜잭션은 유지하고 상품 hit의 바깥 트랜잭션을 없앤 구현과 일치한다. 커넥션 획득은 두 번이어도 revision 조회 이후 바로 반환하므로 Redis·응답 조립 중 점유를 줄이는 방향이다. 다만 실제 커넥션 보유 구간별 시간은 별도 계측하지 않았다. `connectionWaitMs`는 인증과 revision의 `getConnection()` 소요 합계이며 순수 revision 대기만을 의미하지 않는다.

## Redis 및 측정 한계

- 본 측정 Redis get 오류 **3,730→3,730**, put 오류 **265→265**: 증가는 0이다. 누적값은 예열 중 발생한 것이므로 전체 실행에서 오류가 없었다고 표현하지 않는다.
- 본 측정 전후 Redis hit **+306,009**, miss·만료·eviction **+0**. GET/MGET 각각 **+102,002**로 캐시 3개를 두 왕복으로 읽었다. 검증·회복 조회를 포함해 본 부하 요청 수보다 조금 크다.
- 부하기 k6 CPU 평균 **380.75%**, 최대 표본 **601%**(8코어 합산 최대 800%). 미시작 0건.
- 자원 지표는 5초 주기 표본이다. pending 0은 관측 표본에서 0이라는 뜻이며 순간 대기가 전혀 없음을 보장하지 않는다. Trace는 1% 표본, k6 응답 시간은 전수다.
- 이번은 1회 결과다. 반복 측정과 만료 시나리오 없이 지속적인 SLO 보장이나 스탬피드 해결을 주장하지 않는다.

## 재현·원본

- [계획](plan.md), [설정](config.json), [실행 요약](slo-runs.json)
- [배포 기록](deployment.txt), [base commit·JAR SHA](build.json), [구현 diff](implementation.patch)
- [본 측정](results/20261015T133715-measure-1700-8905f8/slo-result.json), [서버 로그](results/20261015T133715-measure-1700-8905f8/server.log), [자원 집계](results/20261015T133715-measure-1700-8905f8/resource-observations.json), [Redis 상태](results/20261015T133715-measure-1700-8905f8/cache-state.json)
- [변경 전 26번 보고서](../26-cache-display-mget/report.md)

공통 `run_combined_slo.py`·`RemoteRunner`·`products.js`·`summarize_combined_slo.py`를 재사용했다. 종료 후 수정 버전을 캐시 ON·revision 우회 OFF 상태로 유지한다. 예전 우회 실험 버전으로 되돌리지 않았다.
