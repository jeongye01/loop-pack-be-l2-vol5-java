# 외부 API 연동 실험

외부 결제 호출을 제품 DB 트랜잭션 밖에서 수행했을 때의 전체 HTTP 응답시간과 주문 정합성을 측정한다. 제품 API와 도메인 모델은 변경하지 않고, 각 실험 폴더의 가짜 외부 API와 실행 지원 코드만 사용한다.

- [01. 비관적 락·결제 승인 300ms](01-pessimistic-pool40/plan.md)
- [02. 비관적 락·결제 승인 5초](02-pessimistic-delay5s/plan.md)
- [03. 낙관적 락·결제 승인 5초](03-optimistic-delay5s/plan.md)

각 실험 결과의 `gitSha`, 환경 스냅샷, 요청 trace, DB 검증 JSON을 함께 보존한다.
