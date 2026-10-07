# 동시성 실험 계획서: SERIALIZABLE + 데드락 1회 재시도

- 실험 ID: 02
- 상태: 동시성 테스트 10회·S1 본 측정 9회 완료
- 공통 조건: [재고 경쟁 실험](../README.md)
- 기준선: [01 SERIALIZABLE 결과](../01-serializable/report.md)

## 질문과 가설

질문: S1의 동일 상품 재고 경합에서 데드락 요청을 새 트랜잭션으로 1회 재시도하면 기술 오류가 줄어드는가?

가설: 재시도로 일부 데드락 요청이 성공 또는 품절 거절로 재판정되어 기술 오류가 줄어든다. 재시도 횟수는 1회로 제한하고 대기 시간은 0ms로 고정한다.

## 변경 사항

`concurrency.transaction-retry.max-retries=1`, `concurrency.transaction-retry.backoff-ms=0`을 주입한다. 각 시도는 `REQUIRES_NEW` 트랜잭션에서 주문·상품·사용자 상태를 다시 조회한다. 재시도 후에도 잠금 획득에 실패하면 기술 오류로 기록한다.

HTTP 부하는 01과 동일한 S1, 10·30·100 RPS, 각 60초·3회로 실행한다. 결과는 01의 전체·성공·품절·기술 오류 TPS와 p50·p95·p99를 비교한다.

## 적용 전 기록

01 실험에서는 30 RPS부터 데드락이 관찰됐고 100 RPS에서는 매회 발생했다. 데이터 정합성은 통과했지만 데드락 요청이 기술 오류로 남았다.

실행 방법은 [support/README.md](support/README.md), 결과는 `report.md`에 기록한다.
