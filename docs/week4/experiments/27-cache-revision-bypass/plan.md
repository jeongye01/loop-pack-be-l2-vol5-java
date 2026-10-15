# Revision 및 바깥 읽기 트랜잭션 우회 비교

- 요청: revision DB 왕복 비용을 제외하여 고정 데이터 캐시 hit 성능을 비교한다. 기본 정합성 설계의 교체가 아니다.
- 옵션: `experiment.catalog-revision-bypass.enabled=true` (`EXPERIMENT_CATALOG_REVISION_BYPASS_ENABLED=true`). 기본 false, experiment profile 없이 true이면 시작 거부.
- true에서는 DB revision 대신 프로세스 시작 시 만든 `experiment-UUID`를 사용한다. 기존 정상 캐시·이전 실험 캐시와 키를 분리하며 TTL 300±30초와 사용자별 키 경계는 유지한다. 프로세스 간 캐시 공유를 검증하는 실험이 아니다.
- 상품 facade의 바깥 읽기 트랜잭션도 생략한다. 그렇지 않으면 SQL 없이도 트랜잭션 시작/종료의 DB 왕복이 남는다. 기본 false에서는 기존 read-only REQUIRED 트랜잭션과 DB snapshot을 유지한다.
- 인증 DB 조회는 유지한다. miss 원본 조회는 기존 use case 트랜잭션을 사용한다. 따라서 hit 예상 SQL은 인증 1회, 상품 facade의 JDBC 연결 획득 0회다.
- 캐시 변경·삭제 무효화, 동시 쓰기 일관성은 이 옵션에서 보장하지 않는다. 사용자 요청에 따른 **쓰기 없는 성능 진단 전용**이다. 기존 정합성 회귀 테스트는 기본 설정으로 유지하며 기대값을 완화하지 않는다.
- 비교: [26번](../26-cache-display-mget/report.md) 대비 캐시 GET+MGET·조합·TTL·인증 유지. revision SQL과 바깥 트랜잭션을 동시에 제외하므로 결과를 SQL 하나만의 효과로 해석하지 않는다.
- 부하: 같은 최신순 첫 20개·seed, 앱 재시작 → 300 RPS 20초 → 회복 확인 → 1,700 RPS 60초 1회, VU3,000·timeout5초·Trace1%. 예열 실패도 수집/회복이 정상이면 본 측정. 충분한 안정화가 아닌 탐색 비교임을 기록한다.
- SLO: p99 ≤200ms, 성공률100%, 미시작/미완료0. SQL·커넥션·Redis 명령·TTL·CPU·JIT·실패를 함께 수집한다.
