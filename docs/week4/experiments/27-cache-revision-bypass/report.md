# Revision 확인·바깥 트랜잭션 우회 결과

커밋 `721e264`. 사용자의 원인 분리 요청으로, **쓰기 없는 데이터에서 revision DB 조회와 상품 facade의 바깥 읽기 트랜잭션을 함께 생략**했다. 인증 DB 조회·GET+MGET·응답 조합·TTL 300±30초는 유지했다. 정상 변경/삭제 정합성 정책을 대체하는 구현이 아니다.

앱 재시작 → 300 RPS 20초 예열 → 회복 → 1,700 RPS 60초, VU3,000·timeout5초·Trace1%. 동일 seed·최신순 첫20개, 별도 EC2 부하기. 충분한 JVM 안정화를 보장하지 않는 각 1회의 탐색 비교다.

| 항목 | 26번 revision 확인 | 이번 우회 |
| --- | ---: | ---: |
| 시작·완료 | 55891 | 88838 |
| 성공 | 55870 | 88838 |
| 성공률 | 99.9624% | 100.0000% |
| timeout | 21 | 0 |
| HTTP 오류 | 0 | 0 |
| 미시작 | 46109 | 13163 |
| 평균 ms | 3222.53 | 1989.91 |
| p99 ms | 4352.1 | 3170 |
| 실제 HTTP RPS (drain 포함) | 890.48 | 1439.09 |

SLO(p99 ≤200ms, 성공률100%, 미시작/미완료0): **미달**. 목표 요청량 102,000. 미완료 0·응답 불일치 0. 실행 경계의 시작+미시작 값은 원본 그대로 보존한다. 수집 및 회복 정상: True.

## SQL·커넥션 관측

- Trace 표본 828건. 요청당 SQL: {'min': 1, 'max': 7, 'mean': 1.0434782608695652, 'p99': 3}. revision SQL 총 0회.
- 요청별 JDBC acquire 횟수 분포: {1: 817, 3: 5, 2: 5, 4: 1}. 인증을 제외한 상품 hit의 DB 접근 제거 여부를 확인한다.
- 표본 커넥션 대기 평균 12.747ms, 서버 처리 평균 140.853ms.
- Hikari active 최대 40.0, pending 최대 73.0.
- 앱 CPU 평균 182.98% (1코어=100%, 앱2코어). MySQL CPU 평균 40.67%.
- Redis GET/MGET 증가: {'get': 88572, 'mget': 88738}. 사전·회복 요청 포함.
- Redis hit/miss/expired/evicted 증가: {'keyspace_hits': 266051, 'keyspace_misses': 0, 'expired_keys': 0, 'evicted_keys': 0}. 전역 만료 통계는 다른 키도 포함한다. 실험 대상 키의 TTL은 cache-state 원본에 보존한다.
- Redis 오류 카운터 전/후: ['catalog_cache_errors_total{application="commerce-api",operation="get"} 9314.0', 'catalog_cache_errors_total{application="commerce-api",operation="put"} 11997.0'] / ['catalog_cache_errors_total{application="commerce-api",operation="get"} 10872.0', 'catalog_cache_errors_total{application="commerce-api",operation="put"} 12185.0'].

## 예열 및 fallback

예열 6,001건 중 성공246·timeout5,755, p99 5,002ms. Redis get 오류9,314·put 오류11,997가 발생했고 서버 표본에 원본 조회가 섞였다. 캐시 만료는 없었으며 재적재가 관측됐다. 예열의 오류는 revision SQL 때문이라고 해석할 수 없다.

본 측정도 get 오류 +1,558·put 오류 +188, SQL1회 표본817건/828건(98.67%)과 원본 fallback이 혼재했다. 오류 카운터에는 예외 종류가 없어 Redis 오류의 최초 원인을 확정하지 않는다. Redis 서버 CPU가 낮아도 클라이언트/네트워크/앱 스케줄링 지연을 배제할 수 없다.

본 측정 preflight→recovery compilation time 증가 73508ms, GC pause 증가 0.734초. compilation time은 CPU 사용량 자체가 아니다.

## 해석 범위

커넥션 대기 평균은 약180→13ms, 실제 시작은55,891→88,838건으로 개선됐다. revision 확인과 바깥 트랜잭션을 함께 제거한 것이 연결 대기 감소에 기여했을 가능성을 지지한다. 그러나 CPU 포화와 Redis 오류가 남았고 예열 상태도 달라 순수한 정상 hit 성능이나 revision SQL 단독의 인과 효과는 확인하지 못했다.


revision SQL뿐 아니라 이를 감싼 바깥 트랜잭션의 연결 점유/시작/종료 비용도 함께 제외했다. 따라서 차이를 SQL 하나만의 효과라고 할 수 없다. Redis로 revision을 옮기거나 무효화 정책을 교체한 실험도 아니다. 정합성 보장을 유지하면서 같은 성능을 내는 설계는 별도로 검증해야 한다.

기본 옵션은 false. experiment profile 없이 활성화하면 시작 거부. 프로세스별 experiment UUID로 정상 캐시 및 다른 실행의 키와 격리한다. 실험 모드에서는 변경 후 최신성·삭제 비노출을 보장하지 않으므로 쓰기 없는 진단에만 사용한다.

백엔드477개·Checkstyle·ArchUnit 통과. 새 테스트에서 상품 facade hit의 SQL0/커넥션0·HTTP 인증 유지·TTL·키 격리를 검증했고, 기존 정합성 회귀는 기본 설정으로 유지했다.

[집계](slo-runs.json) · [자원](results/20261015T033140-measure-1700-14effc/resource-observations.json) · [Redis/TTL](results/20261015T033140-measure-1700-14effc/cache-state.json) · [배포](deployment.json) · [비교 기준](../26-cache-display-mget/report.md).

배포 상태: 캐시 ON, revision 우회 옵션 true인 실험 빌드 유지. 우회를 끄려면 `revision-bypass-compose.yml`을 제외한 기존 Compose 목록으로 재생성하거나 옵션을 false로 설정한다.

## 후속: 동일 빌드 추가 예열 결과

[28번](../28-revision-bypass-warmed/report.md)에서 앱 재시작 없이300→700 RPS 각30초 예열 후 같은1,700 RPS60초를 실행하자 p99 52ms·성공102,001건·미시작/timeout0이었다. 본 문서의 수초 지연은 안정화되지 않은 실행의 결과로 보존하며, revision 또는 캐시 조합의 정상 성능으로 일반화하지 않는다.
