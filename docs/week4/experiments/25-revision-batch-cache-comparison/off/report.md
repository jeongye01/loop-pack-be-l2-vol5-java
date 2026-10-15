# 캐시 OFF — 기존 예열 절차 재측정

대표 결과를 이번 재측정으로 교체했다. 이전 raw run과 기존 보고서는 보존한다. 앱721e264 빌드로 설정만 전환했으며 revision 우회false·바깥 read-only 트랜잭션 유지. 앱 재생성 후300→700 RPS 각30초 예열, 회복 확인 후1,700 RPS60초 본 측정1회. 최신순·브랜드 없음·첫20개, VU3,000·timeout5초·Trace1%, 같은 seed·별도 EC2 부하기.

| 단계 | 시작·완료 | 성공률 | 미시작 | timeout | 평균 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| warmup 300 RPS·30초 | 9001 | 4.0329% | 0 | 8567 | 4899.74ms | 5002.00ms |
| warmup 700 RPS·30초 | 20154 | 72.0453% | 847 | 5634 | 3467.55ms | 5001.00ms |
| measure 1700 RPS·60초 | 47082 | 97.7847% | 54919 | 1043 | 3850.91ms | 5001.00ms |

본 측정 SLO(p99≤200ms·성공률100%·미시작/미완료0): **미달**. 목표102,000건, 실제 HTTP RPS(drain 포함)739.46. HTTP 오류0·전송 오류0·응답 불일치0·미완료0건. 수집/회복 정상True.

## 본 측정 근거

- Trace 517건, SQL 횟수 분포{7: 517}, JDBC acquire 횟수{2: 517}.
- revision SQL 총0회. 표본 SQL 오류0회.
- 표본 서버 처리 평균291.488ms, DB 커넥션 대기 평균226.587ms.
- Hikari active/pending 최대40.0 / 161.0.
- 앱 CPU 평균169.96%, MySQL 평균156.27% (1코어=100%, 앱2코어).
- Redis 오류 전[], 후[].
- Redis hit/miss/expired/evicted 증가{'keyspace_hits': 0, 'keyspace_misses': 0, 'expired_keys': 0, 'evicted_keys': 0}. 전역 통계에는 다른 키도 포함한다.
- JVM compilation time 증가59225ms, GC pause 증가1.020초. compilation time은 CPU 사용량 자체가 아니다.

## 해석

한 번의 재측정이며 같은 예열 시간 자체가 같은 JVM 안정화 상태를 보장하지 않는다. 이미 여러 실행을 경험하고 재시작하지 않은28번 revision 우회 결과(p99 52ms)와도 시작 상태가 다르다. 코드/키/TTL/인증 계약은 변경하지 않았다. 각 단계의 오류·전달량을 함께 판단하며 예열 완료를 자동으로 안정화 성공으로 보지 않는다.

[선택한 이번 결과](warmed-runs.json) · [모든 기존 원본](slo-runs.json) · [본 측정 자원](results/20261015T034622-measure-1700-b133e1/resource-observations.json) · [Redis/TTL](results/20261015T034622-measure-1700-b133e1/cache-state.json) · [배포 확인](deployment-warmed.txt).
