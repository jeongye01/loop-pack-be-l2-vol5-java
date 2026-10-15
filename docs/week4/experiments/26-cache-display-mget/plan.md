# 상품 표시 캐시 MGET 비교

- 목적: 목록 GET → 좋아요 수 GET → 재고 GET의 순차 3왕복을 목록 GET → 좋아요 수·재고 MGET의 2왕복으로 줄인다. 상품 ID는 목록에서 얻으므로 목록과 표시값의 두 단계는 유지한다.
- 변경 책임: CachedJson의 일괄 Redis 읽기, ResponseCache의 개별 miss 처리, ProductReadFacade의 표시값 조합. 키·TTL 300±30초·revision·API 응답·트랜잭션 범위는 유지한다. Redis master 사용.
- 검증: 실제 Redis GET/MGET 명령 수, 두 캐시의 부분 miss/손상, 각각의 TTL 및 기존 무효화·장애·늦은 채움·rollback·인증 회귀. 일부 miss는 해당 데이터만 DB에서 조회하고 기존 Spring Cache 설정으로 저장한다.
- 비교 기준: [25번 ON](../25-revision-batch-cache-comparison/report.md). 같은 seed·최신순 첫 20개·VU 3,000·timeout 5초·상세 Trace 1%·캐시 ON. 앱 재시작 후 300 RPS 20초, 회복 확인 후 1,700 RPS 60초 1회. 예열 오류가 있어도 기록/회복이 정상이면 본 측정한다.
- SLO: p99 ≤ 200ms, 성공률 100%, 미시작·미완료 0. 전달량 부족은 달성으로 판정하지 않는다.
- Redis commandstats의 GET/MGET 증가와 revision/auth SQL 2회를 확인한다. cache miss·만료·오류, 앱 CPU·Hikari 대기·Tomcat·JIT를 기록한다. 읽기 전용, TTL 강제 조작 없음.
- 한계: 짧은 예열의 단일 순차 실행은 JVM 안정화와 조건 통제를 보장하지 않는다. 이번에는 DB 커넥션 점유 범위를 바꾸지 않으며 결과 차이를 MGET만의 효과로 단정하지 않는다.
