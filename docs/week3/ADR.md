# 3주차 동시성 설계 ADR

## ADR-001. Product와 Stock의 잠금 범위를 분리한다

- **상태**: 결정 (2026-10-08)
- **범위**: 주문 확정과 상품 삭제·수정이 동시에 실행되는 경우

Product와 Stock이 묶여 있으면 재고 차감에도 Product 행을 배타적으로 잠글 수 있다. 분리하면 Product는 공유락으로 삭제·수정과의 순서를 조정하고, Stock만 배타락으로 잠글 수 있어 재고 경합을 Stock 행에 집중할 수 있다.

| 대상 | 잠금 |
| --- | --- |
| Product | 주문·좋아요·재고 확인 `PESSIMISTIC_READ`, 수정·삭제 `PESSIMISTIC_WRITE` |
| Stock | `PESSIMISTIC_WRITE` 배타락 |
| Point | `@Version` 낙관적 충돌 검사 |
| Order | `@Version` 낙관적 충돌 검사 |

주문 확정은 Product를 `PESSIMISTIC_READ`로 확인하고 Stock을 `PESSIMISTIC_WRITE`로 차감한다. Product 수정·삭제는 `PESSIMISTIC_WRITE`로 주문 확정과의 순서를 조정한다. Point 충전은 `@Version` 충돌을 기본 1회, 주문 확정은 Point 차감까지 포함한 전체 트랜잭션을 2회까지 새 트랜잭션으로 재시도한다.

관리자 재고 변경도 Product `PESSIMISTIC_READ` → Stock `PESSIMISTIC_WRITE` 순서를 사용한다. Product 공유락은 삭제된 상품의 재고 변경을 막고 삭제와의 순서를 보장하며, Stock 배타락은 주문 확정과 재고 수량 갱신이 겹칠 때 갱신 유실과 음수 재고를 막는다.

좋아요 등록은 Product `PESSIMISTIC_READ`로 활성 상품을 확인한 뒤 Like를 저장한다. 상품 삭제가 등록 중간에 끼어드는 것을 막고, 삭제 완료 후에는 새 좋아요를 거절한다.

포인트 충전은 주문 확정과 별도 경로이므로 Point의 `@Version`을 사용한다. 충돌이 발생하면 현재 잔액을 다시 읽는 새 트랜잭션으로 제한 횟수만큼 재시도한다.

브랜드 수정·삭제는 Brand를 `PESSIMISTIC_WRITE`로 잠근다. 브랜드 삭제는 연결 Product도 같은 잠금으로 보호한다. 삭제 중 주문·상품 수정이 해당 행을 바꾸지 못하게 하고, 삭제 완료 후에는 새 주문·수정이 거절되도록 한다. Product 수정도 같은 배타락을 사용해 주문 확정 중 상품명·가격이 바뀌지 않게 한다. Product와 Brand에는 별도 낙관적 버전 충돌 경로를 두지 않는다.

상품명·가격은 주문 품목에 보존되므로 Product soft delete 이후에도 기존 주문 정보는 유지된다.

### 대안 검토

- **낙관적 락**: 인기 상품과 경합 수준을 미리 알 수 없고, 충돌 시 재시도가 늘어 요청 부하와 지연 시간이 커질 수 있다.
- **조건부 갱신**: 재고 한 행에는 적용할 수 있지만, 재고·포인트·주문 상태를 함께 변경할 때 영향 행 수 해석과 JPA 상태 동기화를 별도로 관리해야 한다.
- **선택 이유**: 경합이 집중되는 Stock만 비관적 락으로 보호하고, Point·Order는 낙관적 버전 충돌로 감지·재시도한다.
