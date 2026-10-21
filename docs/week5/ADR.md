# Week 5 ADR

## PG 결제 대조와 취소 결과 확인

- 결제 대조 배치와 취소 결과 확인 배치를 분리하고, 각각 5분마다 실행한다.
- 결제 대조 배치는 `payments.updated_at` 기준 최근 10분의 `SUCCESS`·`FAILED`를 PG 조회 결과와 비교한다. PG의 최종 실패와 로컬 `SUCCESS`가 다르면 PG 취소 요청 없이 결제를 `FAILED`, 주문을 `CANCELED`로 보정하고 확정 차감 재고를 복구한다. 이 보정은 한 DB 트랜잭션으로 처리한다.
- 취소 결과 확인 배치는 실행 시점의 `CANCEL_PROCESSING` 전체를 PG에서 조회한다. PG 취소가 확인된 경우에만 `CANCELED`로 변경하고, 그 외 응답은 다음 실행에서 다시 확인한다.
- PG 응답은 내부 `SUCCESS`, `FAILED`, `CANCELED`, `PARTIALLY_CANCELED`, `PROCESSING`, `UNKNOWN`으로 해석한다. 조회 실패나 미확정 결과는 로컬 상태를 바꾸지 않는다.
- 배치는 `job.name`으로 각각 실행하며, 5분 주기 실행은 배포 환경의 외부 스케줄러가 담당한다.
