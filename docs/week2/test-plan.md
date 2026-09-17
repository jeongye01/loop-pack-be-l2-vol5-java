# commerce-api 테스트 계획

설계에서 정한 불변식이 지켜지는지 테스트로 확인하는 계획이다. 불변식은 [도메인 관계](./domain-relations.md), 기대값은 [API 계약](./api-contract.md)을 따르고, 근거는 [요구사항 문서](./requirements.md)의 ID를 가리킨다.

## 원칙

## 패키지

기능을 먼저 나누고, 기능 안에서 계층으로 나눈다(feature-first). 테스트는 `src/test/java`에서 대상과 **같은 패키지**에 `대상이름Test`로 둔다. 예: `src/main/java/com/loopers/product/domain/Stock.java`의 테스트는 `src/test/java/com/loopers/product/domain/StockTest.java`이다.

| 기능        | 담는 도메인                          |
| --------- | ------------------------------- |
| `brand`   | Brand                           |
| `product` | Product, Stock                  |
| `like`    | Like                            |
| `user`    | User, Point                     |
| `order`   | Order, OrderItem, PaymentResult |

## 테스트 종류

| 종류       | 확인하는 것                                           | 도구                                                         |
| -------- | ------------------------------------------------ | ---------------------------------------------------------- |
| 도메인 단위   | VO·애그리거트·도메인 서비스의 불변식과 기대값                       | JUnit. Spring과 DB 없이 실행한다. 리포지토리가 필요한 판단은 메모리 리포지토리로 확인한다. |
| 유스케이스 통합 | 여러 대상을 한 번에 저장하는지, 거절되면 저장된 값이 그대로인지, 요청자를 구분하는지 | `@SpringBootTest`, MySQL 테스트 컨테이너, `DatabaseCleanUp`       |
| 리포지토리 통합 | 저장 후 다시 조회한 값과 관계, 조회 조건                         | 저장 → `flush`·`clear` → 다시 조회                               |
| HTTP     | API 계약의 성공과 대표 오류, 요청자 구분, 거절 시 기존 값 유지          | MockMvc                                                    |

## 테스트할 도메인

### VO

| 대상            | 애그리거트 | 테스트                              | 확인할 불변식과 규칙                                                                                                          | 근거                                                                                 |
| ------------- | ----- | -------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Stock         | 상품    | `product/domain/StockTest`       | 수량은 0 이상이다. · 차감하면 줄어든 수량이 된다(5에서 2를 빼면 3). · 재고보다 많이 차감하면 거절한다(5에서 6).                                              | R-ADMIN-08, R-ORDER-08, R-ORDER-10, R-ORDER-11                                     |
| Point         | 사용자   | `user/domain/PointTest`          | 잔액은 0 이상이고 표현 범위 안이다. · 충전하면 잔액이 늘어난다. · 충전액은 양의 정수다. · 충전 후 잔액이 범위를 넘으면 거절한다. · 결제하면 잔액이 줄어든다. · 잔액보다 많이 결제하면 거절한다. | R-POINT-03, R-POINT-04, R-POINT-05, R-POINT-06, R-POINT-07, R-ORDER-09, R-ORDER-11 |
| OrderItem     | 주문    | `order/domain/OrderItemTest`     | 상품, 상품 이름, 단가가 정해져 있다. · 수량은 양수다. · 품목 금액은 수량 × 단가다.                                                                 | R-ORDER-02, R-ORDER-06, P-ORDER-03, P-ORDER-07                                     |
| PaymentResult | 주문    | `order/domain/PaymentResultTest` | 결제액과 결제 시점이 모두 정해져 있다.                                                                                               | R-ORDER-12, P-ORDER-06                                                             |

### Entity

| 대상      | 애그리거트    | 테스트                          | 확인할 불변식과 규칙                                                                                                                                                                                                                             | 근거                                                                                                                                              |
| ------- | -------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Product | 상품 (루트)  | `product/domain/ProductTest` | 이름은 1~~100자이고 공백만으로 이루어질 수 없다. · 가격은 1원~~1,000,000,000원이다. · 소속 브랜드가 정해져 있고 바뀌지 않는다. · 재고 0에서 시작한다. · 재고를 0 이상의 최종 수량으로 설정한다. · 삭제된 상품은 수정, 재고 설정, 재고 차감, 다시 삭제가 거절되고 상태가 그대로다. · 재고 차감이 거절되면 재고가 그대로다.                                 | R-ADMIN-05, R-ADMIN-06, R-ADMIN-07, R-ADMIN-08, R-ADMIN-13, R-ORDER-07, R-ORDER-08, P-ADMIN-02, P-ADMIN-03, P-ADMIN-04, P-ADMIN-05, P-ADMIN-06  |
| Brand   | 브랜드      | `brand/domain/BrandTest`     | 이름은 1~50자이고 공백만으로 이루어질 수 없다. · 삭제된 브랜드는 수정과 다시 삭제가 거절되고 상태가 그대로다. · 삭제하면 삭제 여부만 바뀐다.                                                                                                                                                    | R-ADMIN-13, R-ADMIN-14, R-ADMIN-15, P-ADMIN-01, P-ADMIN-06                                                                                      |
| User    | 사용자 (루트) | `user/domain/UserTest`       | 충전한 적이 없으면 잔액은 0이다. · 충전과 결제는 Point를 거친다. · 거절되면 잔액이 그대로다.                                                                                                                                                                              | R-POINT-05, R-POINT-08, R-ORDER-10, P-POINT-01                                                                                                  |
| Like    | 좋아요      | `like/domain/LikeTest`       | 어떤 고객이 어떤 상품을 좋아요했는지 정해져 있다. · 본인의 좋아요만 취소한다.                                                                                                                                                                                           | R-LIKE-01, R-LIKE-04                                                                                                                            |
| Order   | 주문 (루트)  | `order/domain/OrderTest`     | 구매자가 정해져 있다. · 품목은 하나 이상이다. · 같은 상품은 수량을 합산해 품목 하나로 만든다. · 합계는 품목 금액의 합이다. · 생성하면 `DRAFT`이고 결제 결과가 없다. · `DRAFT` 주문에서만 본인이 품목 수량을 바꾸고, 단가는 그대로이며 합계가 다시 계산된다. · 확정하면 `CONFIRMED`가 되고 결제 결과가 생긴다. · 이미 확정된 주문의 확정은 거절되고 상태와 결제 결과가 그대로다. | R-ACCESS-03, R-ORDER-02, R-ORDER-03, R-ORDER-06, R-ORDER-12, R-ORDER-15, P-ORDER-01, P-ORDER-02, P-ORDER-03, P-ORDER-04, P-ORDER-05, P-ORDER-06 |

### 도메인 서비스

클래스 이름은 구현할 때 정한다.

| 대상           | 패키지              | 확인할 불변식과 규칙                                                                                                                                 | 근거                                                                                              |
| ------------ | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| 브랜드 삭제 가능 여부 | `brand/domain`   | 삭제되지 않은 상품이 연결되어 있으면 삭제할 수 없다. 재고 0인 상품도 포함한다. · 연결된 상품이 모두 삭제되었으면 삭제할 수 있다.                                                                | R-ADMIN-02, R-ADMIN-03                                                                          |
| 브랜드 이름 중복 여부 | `brand/domain`   | 삭제되지 않은 브랜드와 이름이 같으면 중복이다. · 삭제된 브랜드와 이름이 같은 것은 중복이 아니다. · 수정할 때는 자기 자신을 빼고 비교한다.                                                           | P-ADMIN-01                                                                                      |
| 상품 이름 중복 여부  | `product/domain` | 같은 브랜드의 삭제되지 않은 상품과 이름이 같으면 중복이다. · 다른 브랜드의 상품, 삭제된 상품과 이름이 같은 것은 중복이 아니다. · 수정할 때는 자기 자신을 빼고 비교한다.                                         | P-ADMIN-02                                                                                      |
| 좋아요 중복 여부    | `like/domain`    | 같은 고객·같은 상품의 좋아요가 있으면 중복이다.                                                                                                                 | R-LIKE-02                                                                                       |
| 주문 확정        | `order/domain`   | 모두 통과하면 재고 차감, 결제, 확정이 함께 일어난다(잔액 10,000, 합계 7,000 → 잔액 3,000). · 남의 주문, 이미 확정된 주문, 삭제된 상품, 재고 부족, 잔액 부족 중 하나라도 있으면 거절하고 주문·재고·잔액이 모두 그대로다. | R-ACCESS-03, R-ORDER-07, R-ORDER-08, R-ORDER-09, R-ORDER-10, R-ORDER-11, R-ORDER-12, P-ORDER-04 |

## 요구사항 배치

요구사항과 정책을 테스트 파일에 나눠 둔다. 한 요구사항이 여러 층에서 확인되면 여러 파일에 둔다. 도메인 밖의 테스트 파일 이름은 구현할 때 정하고, 여기서는 기능과 종류로 적는다.

| 테스트 파일 | 배치된 요구사항 |
| --- | --- |
| `product/domain/StockTest` | R-ADMIN-08, R-ORDER-08, R-ORDER-10, R-ORDER-11 |
| `product/domain/ProductTest` | R-ADMIN-05, R-ADMIN-06, R-ADMIN-07, R-ADMIN-08, R-ADMIN-13, R-ADMIN-14, R-ORDER-07, R-ORDER-08, P-ADMIN-02, P-ADMIN-03, P-ADMIN-04, P-ADMIN-05, P-ADMIN-06 |
| `product/domain` 상품 이름 중복 여부 | P-ADMIN-02 |
| `brand/domain/BrandTest` | R-ADMIN-13, R-ADMIN-14, R-ADMIN-15, P-ADMIN-01, P-ADMIN-06 |
| `brand/domain` 브랜드 삭제 가능 여부 | R-ADMIN-02, R-ADMIN-03 |
| `brand/domain` 브랜드 이름 중복 여부 | P-ADMIN-01 |
| `like/domain/LikeTest` | R-LIKE-01, R-LIKE-04 |
| `like/domain` 좋아요 중복 여부 | R-LIKE-02 |
| `user/domain/PointTest` | R-POINT-03, R-POINT-04, R-POINT-05, R-POINT-06, R-POINT-07, R-ORDER-09 |
| `user/domain/UserTest` | R-POINT-08, R-ORDER-10, P-POINT-01 |
| `order/domain/OrderItemTest` | R-ORDER-02, R-ORDER-06, P-ORDER-03, P-ORDER-07 |
| `order/domain/PaymentResultTest` | R-ORDER-12, P-ORDER-06 |
| `order/domain/OrderTest` | R-ACCESS-03, R-ORDER-01, R-ORDER-02, R-ORDER-03, R-ORDER-06, R-ORDER-12, R-ORDER-15, P-ORDER-01, P-ORDER-02, P-ORDER-03, P-ORDER-04, P-ORDER-05, P-ORDER-06 |
| `order/domain` 주문 확정 | R-ACCESS-03, R-ORDER-07, R-ORDER-08, R-ORDER-09, R-ORDER-10, R-ORDER-11, R-ORDER-12, P-ORDER-04 |
| 요청자 구분 HTTP | R-ACCESS-01, R-ACCESS-02, R-ACCESS-04, R-ACCESS-05, P-ACCESS-01 |
| `brand` 유스케이스·리포지토리 통합 | R-ADMIN-01, R-ADMIN-02, R-ADMIN-14, R-ADMIN-15, P-ADMIN-01, P-ADMIN-06 |
| `brand` HTTP | R-ADMIN-01, R-ADMIN-12, R-CATALOG-01, R-CATALOG-07, P-ADMIN-07, P-ADMIN-08 |
| `product` 유스케이스·리포지토리 통합 | R-ADMIN-04, R-ADMIN-05, R-ADMIN-08, R-ADMIN-12, R-CATALOG-04, R-CATALOG-05, R-CATALOG-06, R-LIKE-05, P-ADMIN-02, P-ADMIN-05, P-CATALOG-02, P-CATALOG-03, P-CATALOG-04, P-CATALOG-08 |
| `product` HTTP | R-ACCESS-06, R-ADMIN-04, R-ADMIN-06, R-ADMIN-08, R-ADMIN-09, R-ADMIN-13, R-CATALOG-02, R-CATALOG-03, R-CATALOG-07, R-CATALOG-08, P-ADMIN-03, P-ADMIN-04, P-ADMIN-07, P-ADMIN-08, P-ADMIN-09, P-CATALOG-01, P-CATALOG-05, P-CATALOG-06, P-CATALOG-07 |
| `like` 유스케이스·리포지토리 통합 | R-LIKE-02, R-LIKE-03, R-LIKE-05, R-LIKE-06, R-LIKE-07, R-LIKE-08, P-LIKE-01 |
| `like` HTTP | R-LIKE-01, R-LIKE-04, R-LIKE-06, P-ACCESS-02, P-LIKE-01, P-LIKE-02 |
| `user` 포인트 유스케이스·리포지토리 통합 | R-POINT-01, R-POINT-02, R-POINT-06, R-POINT-08, P-POINT-01 |
| `user` 포인트 HTTP | R-POINT-01, R-POINT-02, R-POINT-04, R-POINT-06, R-POINT-07, R-POINT-08 |
| `order` 유스케이스·리포지토리 통합 | R-ADMIN-10, R-ADMIN-14, R-ORDER-04, R-ORDER-05, R-ORDER-10, R-ORDER-11, R-ORDER-13, P-ADMIN-10, P-ORDER-07, P-ORDER-09 |
| `order` HTTP | R-ADMIN-10, R-ADMIN-11, R-ORDER-01, R-ORDER-05, R-ORDER-06, R-ORDER-13, R-ORDER-14, P-ACCESS-02, P-ADMIN-10, P-ORDER-01, P-ORDER-04, P-ORDER-07, P-ORDER-08, P-ORDER-09 |
| 연결 흐름 HTTP | R-POINT-02, R-POINT-06, R-ORDER-11, R-ORDER-12, R-ORDER-14 |

## 테스트 설계 기법

요구사항마다 아래 기법 중 필요한 것을 골라 시나리오를 만든다.

| 기법 | 뜻 | 테스트할 값 | 쓰는 곳 |
| --- | --- | --- | --- |
| 경계값 분석 | 결함은 범위의 가장자리에서 자주 나오므로 경계를 확인한다. | 최소값, 최대값, 최소 바로 아래, 최대 바로 위, 명목값(선택) | 범위가 있는 수량, 금액, 길이 |
| 동등 클래스 분할 | 같은 결과를 내는 입력을 한 묶음으로 보고 묶음마다 대표값 하나만 확인한다. | 유효한 클래스와 유효하지 않은 클래스에서 하나씩 | 허용과 거절이 나뉘는 입력 |
| 의사결정표 | 결과가 여러 조건에 따라 달라질 때 조건 조합마다 결과를 확인한다. | 모든 조건 조합 | 조건이 여럿인 판단 |
| 상태 전이 | 상태에 따라 같은 요청의 결과가 달라질 때, 전이마다 결과를 확인한다. | 허용되는 전이와 막히는 전이 | 주문 상태, 삭제 여부, 좋아요 유무 |
| 오류 추측 | 경험상 깨지기 쉬운 곳을 골라 확인한다. | 필수값 비움, 잘못된 형식, 예상치 못한 행동(반복 요청, 부분 실패 등) | 입력 검증, 반복 요청, 여러 대상을 함께 바꾸는 흐름 |

## 시나리오 작성

`test-scenario-write` 스킬(`.agents/skills/test-scenario-write`)로 요구사항을 테스트 파일에 배치하고, 요구사항마다 위 기법을 매핑해 시나리오를 작성한다. 시나리오 문서는 [`test-scenarios`](./test-scenarios/) 폴더에 둔다.

## 테스트 설계에서 드러난 질문

| 질문 | 드러난 곳 |
| --- | --- |
| 재고 차감 수량이 0 이하이면 Stock은 어떻게 하는가? 주문 수량은 양수(R-ORDER-06)라 주문에서는 들어오지 않지만, Stock 자체의 규칙은 없다. | Stock 오류 추측 |
| 이름의 앞뒤 공백과 대소문자가 다르면 같은 이름인가? 길이는 앞뒤 공백을 빼고 세는가? | 이름 경계값, 이름 중복 오류 추측 |
| 주문 확정에서 여러 조건이 함께 실패하면 어떤 오류를 알리는가? 우선 시퀀스 다이어그램의 확인 순서(본인 → `DRAFT` → 상품 → 재고 → 잔액)를 따른다. | 주문 확정 의사결정표 |

## 도메인 밖에서 확인할 것

| 종류       | 확인할 것                                                                                                                                 | 근거                                                                                      |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| 유스케이스 통합 | 주문 확정이 거절되면 DB의 주문·재고·잔액이 그대로다. · 충전이 거절되면 저장된 잔액이 그대로다. · 좋아요를 반복해서 등록·취소해도 관계는 하나이거나 없다. · 다른 고객의 주문과 좋아요 목록은 없는 대상으로 다룬다.          | R-ORDER-10, R-POINT-08, R-LIKE-02, P-LIKE-01, P-ACCESS-02                               |
| 리포지토리 통합 | 삭제된 브랜드·상품을 고객 조회에서 뺀다. · 이름 중복 조회가 삭제된 대상을 뺀다. · 좋아요 수를 관계에서 센다. · 상품 목록의 브랜드 필터, 정렬, 페이지                                            | R-ADMIN-12, R-LIKE-05, R-CATALOG-04, R-CATALOG-05, P-CATALOG-02, P-ADMIN-01, P-ADMIN-02 |
| HTTP     | [API 계약](./api-contract.md)의 25개 API마다 성공과 대표 오류 · 관리자·일반 사용자·식별 없는 요청의 구분 · 거절된 요청 뒤 저장된 값 유지 · 연결 흐름: 충전 → 여러 품목 주문 확정 → 내 주문·잔액 조회 | R-ACCESS-04, R-ACCESS-05                                                                |

## 진행 순서

| 순서  | 대상                              | 이유                                       |
| --- | ------------------------------- | ---------------------------------------- |
| 1   | Stock                           | 과제가 대표 TDD 예시로 든 규칙이다. 다른 객체에 의존하지 않는다.  |
| 2   | Point, OrderItem, PaymentResult | 다른 VO도 의존하는 대상이 없다.                      |
| 3   | Product, User                   | Stock과 Point를 가진 루트다.                    |
| 4   | Order                           | OrderItem과 PaymentResult를 가진 루트다.        |
| 5   | Brand, Like                     | 의존하는 VO가 없는 Entity다.                     |
| 6   | 도메인 서비스                         | 여러 애그리거트를 함께 본다. 주문 확정을 마지막에 둔다.         |
| 7   | 기능별 유스케이스 → 리포지토리 → HTTP        | 브랜드 → 상품 → 좋아요 → 포인트 → 주문 순서로 바깥까지 연결한다. |
| 8   | 연결 흐름                           | 충전 → 주문 확정 → 조회를 HTTP로 확인한다.             |

