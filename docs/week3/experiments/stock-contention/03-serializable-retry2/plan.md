# 동시성 실험 계획서: 재시도 2회·0ms

- 실험 ID: 03
- 기준선: [02 SERIALIZABLE + 1회 재시도](../02-serializable-retry/report.md)
- 공통 조건: [재고 경쟁 실험](../README.md)

## 질문과 가설

질문: S1 동일 상품 경합에서 재시도 2회·0ms이 02 기준선보다 기술 오류를 줄이는가?

가설: 재시도 기회를 한 번 더 주면 데드락 후 요청이 성공 또는 품절 거절로 재판정되는 비율이 높아진다.

## 변경 사항

`concurrency.transaction-retry.max-retries=2`, `concurrency.transaction-retry.backoff-ms=0`을 주입한다. 각 시도는 새 `REQUIRES_NEW` 트랜잭션에서 최신 상태를 재조회한다.

S1을 10·30·100 RPS, 각 60초·3회로 실행하고 02와 기술 오류·TPS·p50·p95·p99를 비교한다.
