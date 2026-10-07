# 동시성 실험 계획서: 낙관적 버전 락 1회 재시도

- 실험 ID: 11
- 상태: 실행 완료
- 작성일: 2026-10-06
- 실행 버전: `451e27b` (실험 산출물 생성 시 working tree 변경 포함)
- 공통 절차·결과 형식: [프로토콜](../../protocol.md)
- 시스템 스펙·시나리오·부하: [재고 경쟁 실험](../README.md)

## 질문과 가설

실험 10의 낙관적 버전 충돌에 새 트랜잭션 1회 재시도와 0ms 백오프를 적용하면 충돌 요청 일부가 성공으로 전환되는지 확인한다. 재시도 요청도 같은 상품 경쟁에 다시 참여하므로, 높은 부하에서는 잔여 충돌과 p95가 증가할 수 있다.

## 변경 사항

실험 10과 동일하게 DB 기본 격리수준(REPEATABLE-READ), JPA `@Version`, Hikari 풀 200개를 사용한다. 변경값은 `concurrency.transaction-retry.max-retries=1`, `concurrency.transaction-retry.backoff-ms=0`이다. 실패한 트랜잭션을 재사용하지 않고 새 트랜잭션으로 한 번만 재실행한다.

## 검증 순서

1. `OrderConcurrencyTest` 4개와 격리수준 확인을 10회 실행한다.
2. S1을 10·30·100 RPS, 각 60초·3회 실행한다.
3. 최초 충돌, 재시도 성공, 잔여 충돌, 업무 거절을 구분하고 재고·잔액·주문 상태를 별도 DB 조회로 검증한다.

실행 방법은 [support/README.md](support/README.md), 결과는 [report.md](report.md)에 기록한다.
