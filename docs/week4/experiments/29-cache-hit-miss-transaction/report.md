# hit/miss 트랜잭션 분리 — revision ON 3회 결과

**1,700 RPS·60초·VU 3,000에서 p99 124 / 56 / 96ms. 본 측정 3회 모두 성공률 100%, 미시작·timeout·응답 불일치·미완료 0건으로 SLO 통과.**

캐시 ON·DB revision 확인 유지(우회 OFF). 최신순·무브랜드·page=0·size=20 통합 API, 같은 seed·JAR·별도 EC2 부하기·GET+MGET·TTL 300±30초·상세 Trace 1%다. 각 회차 300→700 RPS 각 30초 예열과 서버 회복 확인 후 본 부하를 실행했다. 1회차는 배포 재시작 후, 추가 2·3회차는 **앱 재시작 없이** 이어 실행했다. TTL과 캐시를 강제로 초기화하지 않았다.

## 전체 요청 결과

| 회차 | 평균 | p95 | p99 | 시작·완료 | 성공률 | 미시작 | timeout | SLO |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 11.66ms | 50ms | **124ms** | 102,000 | 100% | 0 | 0 | 통과 |
| 2 | 7.67ms | 28ms | **56ms** | 102,001 | 100% | 0 | 0 | 통과 |
| 3 | 9.91ms | 43ms | **96ms** | 102,001 | 100% | 0 | 0 | 통과 |

각 회차 목표 102,000건이며 실행 경계의 실제 건수는 그대로 기록했다. SLO는 p99 ≤ 200ms·성공률 100%·미시작/미완료/불일치 0이다. p99는 전수 요청에서 계산했고 회차 간 평균으로 합치지 않았다. 본 측정 수집 항목 및 부하 종료 후 회복 확인도 3회 모두 통과했다.

## 예열 결과

| 회차 | 300 RPS p99 | 700 RPS p99 | 예열 실패 건수 |
| --- | ---: | ---: | ---: |
| 1 | 5001ms | 45ms | 6,057 |
| 2 | 5ms | 7ms | 0 |
| 3 | 5ms | 8ms | 0 |

1회차 첫 예열에는 timeout 6,053건·HTTP 오류 4건이 있었다. 회복 확인 후 700 RPS에서는 정상화됐다. 추가 2·3회차 예열에는 실패·미시작이 없었다. 따라서 최초 기동부터 SLO를 지켰다는 뜻은 아니다.

## 자원과 revision 확인

| 회차 | 서버 표본 수 | SQL 수 범위 | 커넥션 획득 소요 평균 | Hikari active 최대 | pending 최대 | 앱 CPU 평균 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1035 | 2~2 | 0.154ms | 26 | 0 | 148.10% |
| 2 | 1007 | 2~2 | 0.141ms | 20 | 0 | 146.03% |
| 3 | 1020 | 2~2 | 0.474ms | 35 | 2 | 145.07% |

SQL 2회는 인증 SELECT와 DB revision SELECT다. revision을 생략한 결과가 아니다. 변경 전 마지막 회차의 커넥션 획득 소요 평균 28.08ms·pending 최대 163과 비교하면 대기가 크게 줄었다. 변경 전 3회 p99 **618 / 1,326 / 440ms**와 달리 변경 후에는 3회 모두 200ms 이하였다. hit의 바깥 트랜잭션을 제거한 구현과 관측 결과가 일치한다. 다만 JVM 준비 상태와 실행 시점이 동일하지 않아 정확한 인과 배율로 해석하지 않는다.

커넥션 획득 소요는 인증과 revision의 getConnection() 시간을 합친 서버 표본 값이다. 자원은 5초 간격 표본이며 순간 최대를 보장하지 않는다. CPU는 1코어=100%, 앱은 2코어다. 3회차 pending은 최대 2가 관측됐으므로 모든 순간 대기가 없어졌다고 표현하지 않는다.

## Redis와 검증 범위

- 본 측정 3회 모두 Redis get 오류 3,730→3,730, put 오류 265→265로 증가 0. 누적 오류는 최초 예열에서 발생했다.
- 세 본 측정 모두 Redis miss·만료·eviction 증가 0. hit 중심 정상 상태에서의 SLO를 확인했다.
- 전체 실행 중 TTL을 강제로 조작하지 않았다. 본 측정 중 만료가 없었으므로 만료 스탬피드 내성을 검증한 것은 아니다.
- 같은 배포의 연속 3회에서 목표를 재현했다. 장시간 부하·혼합 쓰기·Redis 장애·최초 기동 구간까지 SLO를 보장하지 않는다.

## 재현·원본

[계획](plan.md) · [설정](config.json) · [예열 포함 전체 실행 요약](slo-runs.json) · [첫 회 상세 분석](report-first.md) · [배포 기록](deployment.txt) · [빌드 식별](build.json) · [구현 diff](implementation.patch)

- 1회: [20261015T133715-measure-1700-8905f8](results/20261015T133715-measure-1700-8905f8/slo-result.json), [자원·서버 표본](results/20261015T133715-measure-1700-8905f8/resource-observations.json), [Redis 전후](results/20261015T133715-measure-1700-8905f8/cache-state.json)
- 2회: [20261015T134323-measure-1700-3da718](results/20261015T134323-measure-1700-3da718/slo-result.json), [자원·서버 표본](results/20261015T134323-measure-1700-3da718/resource-observations.json), [Redis 전후](results/20261015T134323-measure-1700-3da718/cache-state.json)
- 3회: [20261015T134717-measure-1700-6711a6](results/20261015T134717-measure-1700-6711a6/slo-result.json), [자원·서버 표본](results/20261015T134717-measure-1700-6711a6/resource-observations.json), [Redis 전후](results/20261015T134717-measure-1700-6711a6/cache-state.json)

[변경 전 26번](../26-cache-display-mget/report.md)과 비교한다. 공통 run_combined_slo.py·RemoteRunner·products.js·summarize_combined_slo.py를 재사용했다. 추가 2회 후에도 동일 배포·캐시 ON·revision 우회 OFF를 유지한다.
