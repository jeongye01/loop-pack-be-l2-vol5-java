# hit/miss 트랜잭션 분리 후 SLO 측정

최신순·무브랜드·첫 20개 통합 상품 API를 고정 데이터로 조회한다. revision DB 확인은 유지하고, 전체 hit에서 바깥 트랜잭션을 제거한 변경을 검증한다. miss에서는 revision부터 같은 트랜잭션에서 다시 읽는다.

- 별도 EC2 부하기, VU 3,000, timeout 5초. 300→700 RPS 각 30초 예열 후 1,700 RPS 60초 본 측정 1회.
- 캐시 ON, revision 우회 OFF, GET+MGET, TTL 300±30초, 상세 Trace 1%. 강제 만료·쓰기 없음.
- 예열 실패도 원본에 남긴다. 기존 절차처럼 수집·회복이 정상이면 본 측정을 진행한다.
- SLO: p99 ≤ 200ms, 성공률 100%, 미시작·미완료·응답 불일치 0.
- 기존 26번 revision ON p99 618/1326/440ms와 비교한다. 앱 재시작·JIT 상태 차이는 별도로 표시한다.
- 커넥션 획득 대기, Hikari active/pending, CPU, 요청별 SQL, Redis 오류/miss/만료와 seed 응답 일치를 확인한다.
- 공통 run_combined_slo.py·RemoteRunner·products.js·summarize_combined_slo.py를 재사용한다. 배포 전 484개 테스트와 Checkstyle·ArchUnit 통과.

빌드는 baseCommit + implementation.patch 및 JAR SHA로 식별한다. 기존 결과를 변경 후 결과로 덮어쓰지 않는다. 이 실험은 정상 hit 중심 기준선이며 만료 스탬피드 SLO 검증은 아니다.

## 추가 반복 측정

사용자 요청으로 같은 배포에서 앱 재시작 없이 2회 추가한다(총 본 측정 3회). 매회 300→700 RPS 각 30초 예열 후 1,700 RPS 60초, VU 3,000. 회복 확인 후 다음 회차를 시작하며 TTL·캐시를 강제로 초기화하지 않는다. 자연 만료 여부와 오류 증가는 회차별로 기록한다.
