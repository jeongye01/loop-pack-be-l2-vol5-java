# Redis 분산 락으로 혼합 읽기·쓰기 재채움 제어

## 질문

로컬 Single Flight를 Redis SET NX 락으로 교체하면 TTL 만료·구매에 따른 재고 revision 변경 시 원본 조회와 읽기·구매 지연이 어떻게 달라지는가?

## 고정 조건

| 항목 | 값 |
| --- | --- |
| 읽기 | 최신순 첫 페이지 20개, 1,700 RPS, VU 3,000 |
| 쓰기 | 주문 생성 → 확정 10회/초, VU 100, 회차별 신규 구매자 |
| 시간 | 본 측정 60초 × 3회 |
| SLO | 읽기·구매 전체 각각 p99 ≤ 100ms, 성공률 100%, 미시작 별도 표시 |
| TTL | 최신순 첫 페이지 2초, 나머지 기존 300±30초 |
| 예열 | 처음 300/700 RPS 각 30초, 이후 같은 서버에서는 회복 확인만 |
| 정합성 | 기존 RDB revision, hit/miss 트랜잭션 분리 유지 |
| seed 응답 일치 | 생략; 구매 결과·재고·포인트 검증 유지 |

## 구현

1. 첫 miss의 캐시 버킷+revision 포함 키에 `SET NX`와 5초 만료를 원자적으로 적용한다.
2. 소유자는 DB 트랜잭션 안에서 revision·캐시를 다시 확인하고 miss인 부분만 원본 조회·캐시 저장한다. 별도 사전 재조회로 revision SQL을 한 번 더 늘리지 않는다.
3. 나머지 요청은 DB 트랜잭션 밖에서 20ms 대기 후 자기 요청으로 revision·캐시를 재조회한다.
4. 최대 대기 예산 1초 또는 Redis 락 오류 시 기존 DB 조회로 fallback한다. 1초는 HTTP 전체 시간의 상한이 아니다.
5. 해제는 UUID 소유자 토큰을 확인하는 Lua로 실행한다. 예외 시에도 해제를 시도한다.

## 수집·판정

- 읽기 및 구매 전체·생성·확정의 avg/p50/p95/p99, 성공·실패·미시작 수.
- 전체 요청 원본 목록·재고 조회 횟수/동시성, Redis hit/miss/error, CPU, Hikari active/pending, Tomcat busy.
- `catalog.cache.lock` outcome: acquired, contended, timeout_fallback, acquire_error, release_error. contended는 고유 요청 수가 아닌 실패한 락 시도 수이다.
- 공통 `run_mixed_repeats.py` → `run_combined_slo.py` / 기존 k6를 사용한다. 예열·회복을 본 측정과 분리한다.
- 31(무제어)·32(로컬 Single Flight) 결과와 비교한다. 32는 회차마다 예열했으므로 완전히 동일한 절차의 A/B로 주장하지 않는다.

## 한계

- 이번 부하 서버는 앱 1대다. 두 조율자 간 Redis 공유는 테스트로 확인하되 다중 앱 부하 성능을 검증했다고 하지 않는다.
- Redis keyspace hit/miss는 캐시 외 락 Lua GET 등도 포함하는 전역 값이므로 응답 캐시 적중률로 해석하지 않는다.
- 첫 miss만 조율한다. 서로 다른 키·revision, 조합 응답의 후속 miss, 락 임대 만료·eviction·failover·fallback에는 중복 DB 조회가 가능하다.
- 락은 DB 정합성을 대신하지 않는다. 기존 revision 보호를 유지한다.
- 대기 중 DB 커넥션은 잡지 않지만 HTTP 작업 스레드는 점유한다. Redis 폴링·revision 재조회 비용도 포함한다.
- 락 갱신/watchdog 및 별도 Bulkhead는 이번 변경에 추가하지 않는다.
