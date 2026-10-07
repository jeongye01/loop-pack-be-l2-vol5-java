# 동시성 실험 계획서: 낙관적 버전 락

- 실험 ID: 10
- 상태: 실행 완료
- 작성일: 2026-10-06
- 실행 버전: `393db74` (실험 산출물 생성 시 working tree 변경 포함)
- 공통 절차·결과 형식: [프로토콜](../../protocol.md)
- 시스템 스펙·시나리오·부하: [재고 경쟁 실험](../README.md)

## 질문과 가설

기본 격리수준(REPEATABLE-READ)에서 JPA `@Version`을 적용하고 재시도하지 않을 때, 충돌을 감지하면서 동시성 정합성과 처리량을 유지하는지 확인한다. 충돌 요청은 기술 오류로 기록되고 부분 차감 없이 롤백될 것으로 예상한다.

## 변경 사항

`concurrency.lock.strategy=optimistic`으로 일반 조회 경로를 사용하고 모든 엔티티에 JPA `@Version`을 적용한다. 충돌 재시도와 백오프는 0회·0ms로 고정한다. DB 격리수준은 설정하지 않아 MySQL 기본값(REPEATABLE-READ)을 사용하고, Hikari 풀은 최대·최소 유휴 200개로 고정한다.

실제 커넥션의 격리수준은 `IsolationProbeTest`로 매회 확인한다. 기존 동시성 테스트의 기대값은 바꾸지 않고 충돌·기술 오류를 결과에 그대로 기록한다.

## 검증 순서

1. `OrderConcurrencyTest` 4개와 격리수준 확인을 10회 실행한다.
2. S1을 10·30·100 RPS, 각 60초·3회 실행한다.
3. 요청별 성공·업무 거절·낙관적 충돌을 구분하고, 재고·잔액·주문 상태를 별도 DB 조회로 검증한다.

실행 방법은 [support/README.md](support/README.md), 결과는 [report.md](report.md)에 기록한다.
