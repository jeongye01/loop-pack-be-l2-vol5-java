# 캐시 OFF / revision 확인 ON — 예열 조건 맞춘 재측정

사용자 요청으로 대표 결과를 교체했다. 기존 실행 원본과 [교체 전 보고서](report-before-warmed.md)는 보존한다. 이번 코드는721e264로 동일하며, 캐시 설정만 변경했다. 각 설정 전환 시 앱 재생성 → 300 RPS30초 → 700 RPS30초 → 1,700 RPS60초. VU3,000·timeout5초·최신순 무브랜드 첫20개·동일 seed·별도 EC2·Trace1%.

| 조건 | p99 | 성공률(시작 기준) | 성공 건수 | 미시작 | timeout | SLO |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 캐시 OFF | 5001.00ms | 97.7847% | 46,039 | 54,919 | 1,043 | 미달 |
| 캐시 ON·revision 확인 | 618.00ms | 100.0000% | 102,001 | 0 | 0 | 미달 |
| 참고: revision 우회(28번) | 52.00ms | 100.0000% | 102,001 | 0 | 0 | 통과 |

SLO는 p99≤200ms·성공률100%·미시작/미완료0을 함께 판단한다. 목표 요청102,000건에 대한 전달량이 부족하면 성공률만으로 통과시키지 않는다. k6 실행 경계의 실제 요청 수는 그대로 보존했다.

- [OFF 단계별 결과·자원](off/report.md), [선택 원본](off/warmed-runs.json).
- [revision ON 단계별 결과·자원](../26-cache-display-mget/report.md), [선택 원본](../26-cache-display-mget/warmed-runs.json).
- [그대로 보존한 revision 우회 결과](../28-revision-bypass-warmed/report.md).

revision ON은 본 측정 Redis 오류·miss 증가0, 표본1,072건 모두 SQL2회였다. 커넥션 대기는 평균48.73ms, pending 최대160으로 남았다. 다만 JVM compilation time 증가도19,912ms로28번의2,356ms보다 컸으므로 revision 단독 비용으로 단정하지 않는다.

두 재측정은 같은 예열 단계/시간을 사용했으나 안정화 여부는 단계별 결과로 판단한다. 28번은 이미 여러 부하를 경험한 JVM을 재시작하지 않았으므로 출발 상태가 다르다. 단일 순차 실행의 차이를 revision SQL 하나 또는 예열 시간만의 인과 효과로 단정하지 않는다. 현재 폴더의 예전 on/ 원본은 이전 빌드의 기록이며 이번 대표 revision ON은26번에 있다.

제품 코드·API 계약·TTL 변경 없음. 종료 후 사용자 요청 전의 캐시 ON·revision 우회 실험 설정으로 복구하고 health 정상 및 동일 JAR를 확인했다([복구 기록](restored-warmed.txt)). 결과의 비교 가능성과 정합성 보호의 유무는 별개다.
