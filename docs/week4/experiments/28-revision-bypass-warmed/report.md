# 기존 예열 절차로 revision 우회 버전 재측정

**1,700 RPS·60초에서 p99 52ms, 평균5.51ms, 102,001건 전부 성공. 미시작·timeout·미완료·응답 불일치0으로 이번 SLO 통과.**

배포721e264 그대로, 앱 재시작 없음. 캐시 ON·revision/바깥 트랜잭션 우회·인증 DB 조회·GET+MGET·TTL300±30초 유지. 최신순 무브랜드 첫20개·VU3,000·timeout5초·별도 EC2 부하기·Trace1%.

300 RPS30초 → 회복 → 700 RPS30초 → 회복 → 1,700 RPS60초. 19번과 같은 예열 단계/시간을 사용했으나 27번을 이미 실행한 JVM이므로 완전히 동일한 준비 상태는 아니다.

| 단계 | 시작·완료 | 성공률 | 미시작 | timeout | 평균 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| warmup 300 RPS·30초 | 9001 | 100.0000% | 0 | 0 | 2.25ms | 6.00ms |
| warmup 700 RPS·30초 | 21000 | 100.0000% | 0 | 0 | 2.03ms | 7.00ms |
| measure 1700 RPS·60초 | 102001 | 100.0000% | 0 | 0 | 5.51ms | 52.00ms |

본 측정 SLO(p99≤200ms·성공률100%·미시작/미완료0): **통과**. 요청 목표102,000건, 실제 HTTP RPS(drain 포함) 1699.84. HTTP 오류 0·전송 오류 0·응답 불일치 0·미완료 0건. 수집/회복 정상 True.

## 본 측정 관측

- 표본 1054건, SQL 수 {1: 1054}, JDBC 획득 횟수 {1: 1054}.
- revision SQL 0회. 표본 SQL 오류 0회.
- 표본 서버 처리 평균 4.597ms, 커넥션 대기 평균 0.018ms.
- Hikari active/pending 최대 10 / 0. Tomcat busy 평균9.69·최대24 (최대 설정200).
- 앱 CPU 평균 135.41%, MySQL 평균 42.20% (1코어=100%, 앱2코어).
- Redis 오류 카운터 전 ['catalog_cache_errors_total{application="commerce-api",operation="get"} 10872.0', 'catalog_cache_errors_total{application="commerce-api",operation="put"} 12185.0'], 후 ['catalog_cache_errors_total{application="commerce-api",operation="get"} 10872.0', 'catalog_cache_errors_total{application="commerce-api",operation="put"} 12185.0'].
- Redis hit/miss/expired/evicted 증가 {'keyspace_hits': 306012, 'keyspace_misses': 0, 'expired_keys': 0, 'evicted_keys': 0}. 전역 카운터는 다른 키도 포함한다. 대상 TTL은 원본 cache-state에 보존했다.
- JVM compilation time 증가 2356ms, GC pause 증가 0.377초. 컴파일 누적 시간은 CPU 사용량 자체가 아니다.

예열300/700 모두 Redis 오류 증가0, SQL 표본 모두 인증1회. 본 측정 표본1,054건도 전부 인증SQL1회/커넥션획득1회였다. GET/MGET 각각102,003회 증가(점검 포함), SET0, 대상3키 모두 만료 전 TTL 유지. 이번 부하에서는 원본 fallback 징후가 관측되지 않았다.

## 해석 범위

**같은 우회 빌드에서 재시작 없이 추가 예열/실행 후 p99가3,170→52ms로 달라졌다.** 이전 수초 지연을 캐시3개·왕복2회의 고유 비용이나 안정된 정상 상태의 성능으로 해석해서는 안 된다. JVM 컴파일 증가도73,508→2,356ms로 줄었고 Redis 오류·fallback·커넥션 적체가 사라졌다. 준비 상태가 결과에 크게 영향을 준다는 근거지만, JIT 하나가 전체 원인이라고 확정한 것은 아니다.

revision 확인 ON도 같은 안정화 조건으로 재측정해야 정합성 검증 비용을 공정하게 비교할 수 있다. 이번 결과는 우회 상태의 고정 데이터 hit 경로에 한정한다.


재시작·짧은 예열 직후의 [27번](../27-cache-revision-bypass/report.md)은 p99 3,170ms·미시작13,163건·Redis 오류 증가가 있었다. 이번 결과와 비교할 때 추가 예열뿐 아니라 이전 실행으로 누적된 JVM/DB 준비 상태·시간 변화가 함께 달라졌음을 고려한다. 예열만의 인과 효과로 단정하지 않는다.

[19번](../19-cache-sampled-log/report.md)의 p99 51/73/30ms는 완성 응답 캐시1개·인증SQL1회였고, 현재는 캐시3개/왕복2회·응답 조합이 남는다. revision 우회는 변경·삭제 정합성을 보장하지 않는 고정 데이터 실험이다.

[전체 집계](slo-runs.json) · [자원](results/20261015T033904-measure-1700-2dc5dd/resource-observations.json) · [Redis/TTL](results/20261015T033904-measure-1700-2dc5dd/cache-state.json) · [동일 배포 확인](deployment.txt). 종료 후 설정/코드 변경 없이 회복 확인.
