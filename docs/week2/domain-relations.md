# commerce-api 도메인 관계




```mermaid
classDiagram
    direction LR

    class User {
        <<Entity>>
        고객 식별자
    }
    class Point {
        <<Entity>>
        사용자 식별자
        잔액
        충전한다(충전액)
        결제한다(결제액)
    }
    class Brand {
        <<Entity>>
        이름
        삭제 여부
        수정한다(이름)
        삭제한다()
    }
    class Product {
        <<Entity>>
        이름
        가격
        소속 브랜드
        삭제 여부
        수정한다(이름, 가격, 소속 브랜드)
        삭제한다()
    }
    class Stock {
        <<Entity>>
        상품 식별자
        수량
        수량을 설정한다(수량)
        차감한다(수량)
    }
    class Like {
        <<Entity>>
        고객
        상품
        취소 여부
        취소한다(요청자)
        재등록한다(요청자)
    }
    class Order {
        <<Entity>>
        구매자
        품목들 OrderItem
        합계
        상태 DRAFT CONFIRMED
        결제 결과 PaymentResult
        품목 수량을 바꾼다(요청자, 상품, 수량)
        확정한다(결제액, 결제 시점)
    }
    class OrderItem {
        <<VO>>
        상품 식별자
        상품 이름
        수량
        단가
        금액을 구한다()
        수량이 바뀐 새 품목을 만든다(수량)
    }
    class PaymentResult {
        <<VO>>
        결제액
        결제 시점
    }

    Point "1" --> "1" User : 잔액 주체
    Product "N" --> "1" Brand : 속한다
    Stock "1" --> "1" Product : 재고 대상
    Like "N" --> "1" User : 누구의
    Like "N" --> "1" Product : 어떤 상품
    Order "N" --> "1" User : 구매자
    Order "1" --> "1..*" OrderItem : 품목
    Order "1" --> "0..1" PaymentResult : 결제 결과
    OrderItem "N" ..> "1" Product : 생성 시 정보 복사

    note for User "사용자 애그리거트 : User"
    note for Point "포인트 애그리거트 : Point"
    note for Product "상품 애그리거트 : Product"
    note for Stock "재고 애그리거트 : Stock"
    note for Order "주문 애그리거트 : Order + OrderItem + PaymentResult"
```



## 관계


| 관계 | 누가 누구를 아는가 |
| --- | --- |
| Brand 1 ── N Product | Product가 자신이 속한 Brand를 안다. Brand는 Product를 모른다. |
| User 1 ── 1 Point | Point가 잔액의 사용자 식별자를 가진다. User는 Point 상태를 소유하지 않는다. |
| Product 1 ── 1 Stock | Stock이 재고 대상 상품 식별자를 가진다. Product는 Stock 상태를 소유하지 않는다. |
| User 1 ── N Like N ── 1 Product | Like가 User와 Product를 안다. Product는 Like를 모른다. |
| Order 1 ── 1..N OrderItem | Order가 자신의 OrderItem을 가진다. |
| OrderItem → Product | OrderItem은 상품 엔티티를 참조하지 않고 생성 시점의 상품 식별자·이름·단가를 저장한다. |
| User 1 ── N Order | Order가 구매자인 User를 안다. |
| Order 1 ── 0..1 PaymentResult | Order가 자신의 결제 결과를 가진다. |




## 애그리거트와 책임


| 애그리거트 | 루트 | 묶인 값 | 소유하는 것 |
| --- | --- | --- | --- |
| 상품 | Product | 없음 | 이름·가격·소속 브랜드·삭제 여부 |
| 재고 | Stock | 없음 | 상품별 수량·재고 변경 규칙 |
| 사용자 | User | 없음 | 고객 식별자 |
| 포인트 | Point | 없음 | 사용자별 잔액·충전·결제 규칙 |
| 주문 | Order | OrderItem, PaymentResult | 구매자·품목·합계·상태·결제 결과 |





```mermaid
classDiagram
    direction LR

    class OrderConfirmService {
        <<Domain Service>>
        확정한다(요청자, 주문, 상품들, 재고들, 포인트, 결제 시점)
    }
    class BrandRemovalService {
        <<Domain Service>>
        함께 삭제한다(브랜드, 상품들)
    }
    class BrandNameValidator {
        <<Domain Service>>
        이름이 중복인지 본다(이름, 제외할 브랜드)
    }
    class ProductNameValidator {
        <<Domain Service>>
        이름이 중복인지 본다(브랜드, 이름, 제외할 상품)
    }

    OrderConfirmService ..> Order
    OrderConfirmService ..> Product
    OrderConfirmService ..> Stock
    OrderConfirmService ..> Point
    BrandRemovalService ..> Brand
    BrandRemovalService ..> Product
    BrandNameValidator ..> Brand
    ProductNameValidator ..> Product
```


| 도메인 서비스 | 애그리거트 안에 둘 수 없는 이유 |
| --- | --- |
| `OrderConfirmService` | 주문·상품·재고·포인트 네 애그리거트의 상태를 함께 바꿔야 확정이 끝난다 |
| `BrandRemovalService` | `Brand`가 연결 상품을 모르므로 브랜드와 미삭제 상품의 상태를 함께 맞춘다 |
| `BrandNameValidator` | 한 `Brand`는 다른 `Brand` 전부를 볼 수 없다 |
| `ProductNameValidator` | 한 `Product`는 같은 브랜드의 다른 `Product`를 볼 수 없다 |
