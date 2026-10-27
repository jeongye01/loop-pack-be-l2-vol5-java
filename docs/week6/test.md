 

![](orca-paste-1793036057341-42a9a840-4db7-4458-b9be-3e73fefd0d88.png)

## OrderConfirmed 발행 테스트

- 최초 PG SUCCESS: DB commit 전에는 미발행, commit 후 `order-events`에 1건 발행. key·eventId·confirmedAt·상품별 수량이 저장된 주문과 일치하고 topic·partition·offset을 확인한다.
- UNKNOWN 후 대사 SUCCESS: 동일한 확정 경로로 이벤트 1건 발행.
- 미발행: DRAFT·PAYMENT_PENDING·UNKNOWN 유지·결제 실패·확정 TX rollback에서는 이벤트가 없다.
- 중복 방지: 이미 종결된 같은 PG 시도의 반복 적용은 추가 이벤트를 만들지 않는다.
- 이전 POINT 주문: 이번 변경으로 기존 확정 주문 이벤트를 소급 발행하지 않는다.
- payload 기준: 확정 주문에 저장된 품목·수량·confirmedAt을 사용하며 현재 상품 조회값으로 바꾸지 않는다.
- commit 후 발행 실패: 테스트 hook으로 Kafka 전송 직전 실패를 주입한다. 주문 확정과 재고 예약 소비는 commit된 상태이고 Kafka record는 없는지 확인한다.

## 발행 상태 추적

- 이번 주에는 producer 발행 여부를 별도 DB에 저장하지 않는다.
- 운영 추적·복구 정책은 아직 미정이다.
- 테스트에서 주문 DB의 확정 사실과 Kafka record를 대조해 발행 결과를 확인한다.

## 다음 주 전달 사례

- 재현 가능한 테스트 코드에 미발행 사례 1건과 정상 발행·소비 사례 1건을 함께 남긴다.
- 각 사례의 `orderId`, `providerOrderId`, `transactionKey`, `eventId`, `confirmedAt`, 품목과 주문·예약·Kafka·구매 활동 결과를 기록한다. 비밀정보는 제외한다.
- W6 직접 발행 경로를 재현하며, 임시 DB·컨테이너는 관찰값과 코드를 보존한 뒤 정리한다.
