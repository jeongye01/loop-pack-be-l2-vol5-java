# 3주차 동시성 설계 ADR

## ADR-001. Product와 Stock의 잠금 범위를 분리한다

- **상태**: 결정 (2026-10-08)
- **범위**: 주문 확정과 상품 삭제·수정이 동시에 실행되는 경우

Product와 Stock이 묶여 있으면 재고 차감에도 Product 행을 배타적으로 잠글 수 있다. 분리하면 Product는 공유락으로 삭제·수정과의 순서를 조정하고, Stock만 배타락으로 잠글 수 있어 재고 경합을 Stock 행에 집중할 수 있다.

| 대상 | 잠금 |
| --- | --- |
| Product | `PESSIMISTIC_READ` 공유락 |
| Stock | `PESSIMISTIC_WRITE` 배타락 |
| Point | `PESSIMISTIC_WRITE` 배타락 |

잠금 순서는 `Product → Stock → Point → Order`로 고정한다. Product 공유락은 단순 조회가 아니라 상품 삭제·수정의 배타락과 주문 확정의 순서를 조정하기 위한 잠금 읽기에 사용한다.

상품명·가격은 주문 품목에 보존되므로 Product soft delete 이후에도 기존 주문 정보는 유지된다. 삭제와 주문 확정의 동시성 순서를 허용하는 정책으로 바뀌면 Product 공유락은 다시 검토한다.
