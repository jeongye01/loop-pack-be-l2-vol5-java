# 주문 생성·확정 시퀀스

[요구사항](./requirements.md), [도메인 규칙](./domain-rules.yaml), [도메인 관계](./domain-relations.md), [API 계약](./api-contract.md)을 기준으로 주문의 상태 변화를 그린다. 아래의 검사는 업무상 판단 순서이며, DB 잠금·SQL 순서는 아직 정하지 않았다.

## 주문 생성

```mermaid
sequenceDiagram
    autonumber
    actor C as 고객
    participant API as 주문 API
    participant A as application
    participant P as Product
    participant O as Order / OrderItem

    C->>API: POST /api/v1/orders (상품·수량)
    API->>A: 주문 생성 요청
    A->>P: 상품 존재·삭제 여부 확인
    alt 상품이 없거나 삭제됨
        A-->>API: PRODUCT_NOT_FOUND
        API-->>C: 404
    else 주문 가능
        A->>O: 상품 ID·이름·단가와 수량으로 품목 생성
        O->>O: 품목 금액 합산, DRAFT 주문 생성
        A-->>API: 주문 상세
        API-->>C: 201, DRAFT·품목·합계 (payment 없음)
    end
    Note over P,O: 주문 생성만으로 재고와 포인트는 차감되지 않는다.
```

## 주문 확정

`DRAFT` 주문만 확정할 수 있다. 이미 `CONFIRMED`인 주문의 재확정은 `ORDER_ALREADY_CONFIRMED`로 거절한다.

```mermaid
sequenceDiagram
    autonumber
    actor C as 고객
    participant API as 주문 API
    participant A as OrderUseCase.confirm()
    participant R as Repository
    participant S as OrderConfirmService
    participant O as Order
    participant P as Product / Stock
    participant U as User / Point

    C->>API: POST /api/v1/orders/{orderId}/confirm
    API->>A: 요청자·주문 ID 전달
    Note over A: @Transactional 시작
    A->>R: 주문 조회
    R-->>A: Order
    A->>O: 본인 주문 확인
    alt 주문이 없거나 다른 고객의 주문
        A-->>API: ORDER_NOT_FOUND
        API-->>C: 404
    else 본인 주문
        A->>R: 구매자·품목의 현재 상품 조회
        R-->>A: User·Product 목록
        A->>S: 확정 요청(요청자·주문·상품들·구매자·시각)
        S->>O: DRAFT 상태 확인
        S->>P: 상품 사용 가능 여부·품목별 재고 확인
        S->>U: 주문 합계만큼 포인트 잔액 확인
        alt 상태·상품·재고·잔액 검사 실패
            S-->>A: 해당 오류
            A-->>API: 확정 거절
            API-->>C: 409, 해당 오류 코드
            Note over O,U: 확정 거절 시 주문·재고·잔액·결제 결과는 변경되지 않는다.
        else 모든 검사 통과
            S->>P: 품목 수량만큼 재고 차감
            S->>U: 주문 합계만큼 포인트 차감
            S->>O: 결제액·시각 기록, CONFIRMED로 변경
            Note over O,U: 확정 성공 시 재고·잔액이 차감되고 결제 결과가 남는다.
            S-->>A: 확정된 주문
            A-->>API: 주문 상세
            API-->>C: 200, CONFIRMED·품목·합계·payment
        end
    end
```

`OrderUseCase.confirm()`이 트랜잭션 경계다. 정상 반환 시 commit된 뒤 HTTP 성공 응답을 보내고, 예외가 전달되면 rollback 후 오류를 응답한다. 확정 성공 시 재고·포인트·주문 상태·결제 결과는 함께 반영된다. 검사에서 거절되거나 처리 중 실패하면 이번 확정으로 바뀐 상태를 남기지 않는다. 같은 주문·재고·잔액을 동시에 바꾸는 요청의 보호 방식과 순서는 3주차 동시성 설계에서 결정한다.
