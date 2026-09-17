# Green 구현 계획

Green 에이전트가 Red 전체를 실행하고 실패를 분석한 뒤, production 코드를 수정하기 전에 현재 Red를 해결할 작업을 구현 순서대로 기록한다.

일반적인 절차가 아니라 현재 실패를 해결하는 실제 계획을 적는다. 관련 테스트가 완료 조건대로 Green이 된 뒤에만 `[x]`로 바꾼다.

## 구현

- [x] 1. 불변 값 객체 `Stock`, `Point`, `PaymentResult` 구현
  - 요구사항 ID: R-ADMIN-08, R-ORDER-08, R-ORDER-09, R-ORDER-10, R-ORDER-11, R-POINT-03, R-POINT-04, R-POINT-05, R-POINT-06, R-POINT-07, R-ORDER-12, P-ORDER-06, ADR-003
  - 관찰한 Red와 실패 원인: `Stock.decrease`, `Point.charge/pay`가 항상 자기 자신을 반환하고 생성 검증이 없으며, `PaymentResult`가 null 필드를 허용해 관련 경계값·오류 코드 테스트가 실패한다.
  - 필요한 최소 동작: 음수 재고 거절, 양수 차감 및 부족 거절, 0 이하 차감 내부 오류; 양수 충전·overflow 검사·잔액 결제 및 부족 거절; 결제액/시점 null 거절을 구현하고 불변 객체 원본을 유지한다.
  - 변경할 production 파일: `product/domain/Stock.java`, `user/domain/Point.java`, `order/domain/PaymentResult.java`
  - 선행 작업: 없음
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.product.domain.StockTest' --tests 'com.loopers.user.domain.PointTest' --tests 'com.loopers.order.domain.PaymentResultTest'`
  - 완료 조건: 위 세 테스트 클래스가 모두 Green이고 예외 시 원본 값이 유지된다.

- [x] 2. 브랜드 불변식과 브랜드 정책 검증 구현
  - 요구사항 ID: R-ADMIN-02, R-ADMIN-03, R-ADMIN-13, R-ADMIN-14, R-ADMIN-15, P-ADMIN-01, P-ADMIN-06
  - 관찰한 Red와 실패 원인: `Brand`가 이름을 그대로 저장하고 삭제 여부를 항상 false로 반환하며 수정/재삭제를 막지 않는다. 두 validator도 repository 결과를 검사하지 않아 활성 상품·중복 이름을 허용한다.
  - 필요한 최소 동작: 브랜드 이름 trim/1~50자 검증, 논리 삭제 상태 조회와 재삭제·삭제 후 수정 거절; 활성 브랜드 이름 중복(수정 시 자기 ID 제외) 및 활성 연결 상품 존재 시 삭제 거절을 구현한다.
  - 변경할 production 파일: `brand/domain/Brand.java`, `brand/domain/BrandNameValidator.java`, `brand/domain/BrandDeletionValidator.java`
  - 선행 작업: 3번의 `Product.isDeleted()` (삭제된 연결 상품 판정)
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.brand.domain.*'`
  - 완료 조건: 브랜드 도메인 테스트 3개 클래스가 모두 Green이다.

- [x] 3. 상품 불변식과 상품 이름 정책 구현
  - 요구사항 ID: R-ADMIN-05, R-ADMIN-06, R-ADMIN-07, R-ADMIN-08, R-ADMIN-13, R-ADMIN-14, R-ORDER-07, R-ORDER-08, P-ADMIN-02, P-ADMIN-03, P-ADMIN-04, P-ADMIN-05, P-ADMIN-06
  - 관찰한 Red와 실패 원인: `Product`는 이름·가격·초기 재고를 검증/정규화하지 않고 수정·재고 변경·차감·삭제 상태 동작이 비어 있다. 이름 validator도 중복 결과를 검사하지 않는다.
  - 필요한 최소 동작: 상품명 trim/1~100자 및 가격 1~10억 검증, 초기 재고 0, 같은 브랜드 수정만 원자적으로 반영, 삭제 후 모든 변경과 재삭제 거절, `Stock` 결과 반영; 같은 브랜드의 활성 동명 상품(수정 시 자기 ID 제외)을 거절한다.
  - 변경할 production 파일: `product/domain/Product.java`, `product/domain/ProductNameValidator.java`
  - 선행 작업: 1번 `Stock`
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.product.domain.ProductTest' --tests 'com.loopers.product.domain.ProductNameValidatorTest' --tests 'com.loopers.product.domain.StockTest'`
  - 완료 조건: 상품 도메인 테스트 3개 클래스가 모두 Green이고 실패한 수정에서 기존 상태가 유지된다.

- [x] 4. 좋아요 소유권과 중복 판정 구현
  - 요구사항 ID: R-LIKE-01, R-LIKE-02, R-LIKE-04
  - 관찰한 Red와 실패 원인: `Like.cancel`이 요청자 소유권을 검사하지 않고 `LikeDuplicationChecker`가 항상 false를 반환한다.
  - 필요한 최소 동작: 요청자와 소유자가 다르면 `LIKE_NOT_FOUND`, repository에 동일 사용자·상품 좋아요가 있으면 true를 반환한다.
  - 변경할 production 파일: `like/domain/Like.java`, `like/domain/LikeDuplicationChecker.java`
  - 선행 작업: 없음
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.like.domain.*'`
  - 완료 조건: 좋아요 도메인 테스트 2개 클래스가 모두 Green이다.

- [x] 5. 사용자 포인트 상태 위임 구현
  - 요구사항 ID: R-POINT-08, R-ORDER-10, P-POINT-01
  - 관찰한 Red와 실패 원인: 새 `User`의 point가 null이고 `charge/pay`가 비어 있어 초기 잔액·충전 실패 보존·결제 부족 테스트가 실패한다.
  - 필요한 최소 동작: 사용자를 잔액 0으로 초기화하고 `Point`가 성공적으로 반환한 새 값만 필드에 반영해 예외 시 기존 잔액을 유지한다.
  - 변경할 production 파일: `user/domain/User.java`
  - 선행 작업: 1번 `Point`
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.user.domain.*'`
  - 완료 조건: `UserTest`와 `PointTest`가 모두 Green이다.

- [x] 6. 주문 품목 스냅샷과 주문 상태 머신 구현
  - 요구사항 ID: R-ACCESS-03, R-ORDER-01, R-ORDER-02, R-ORDER-03, R-ORDER-06, R-ORDER-12, R-ORDER-15, P-ORDER-01, P-ORDER-02, P-ORDER-03, P-ORDER-04, P-ORDER-05, P-ORDER-06, P-ORDER-07
  - 관찰한 Red와 실패 원인: `OrderItem`은 상품 스냅샷/금액/수량 변경을 구현하지 않았고 수량을 검증하지 않는다. `Order`는 빈 품목, 중복 병합, 합계, 소유권, 초기 상태, 수량 변경, 확정/재확정 로직이 전부 스텁이다.
  - 필요한 최소 동작: 양수 수량 검증과 상품 필드 복사·금액 계산·불변 수량 변경; 주문 생성 시 빈 목록 거절 및 입력 순서를 유지한 productId별 수량 병합, DRAFT/결제 없음 초기화, 합계·소유권, 소유자 우선 확인 후 DRAFT에서만 수량 변경, 한 번만 결제 결과와 CONFIRMED 상태 반영을 구현한다.
  - 변경할 production 파일: `order/domain/OrderItem.java`, `order/domain/Order.java`
  - 선행 작업: 1번 `PaymentResult`, 3번 `Product`
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.order.domain.OrderItemTest' --tests 'com.loopers.order.domain.OrderTest'`
  - 완료 조건: `OrderItemTest`, `OrderTest`가 모두 Green이고 거절 시 품목·상태·결제 결과가 유지된다.

- [x] 7. 주문 확정의 선검증과 일괄 상태 변경 구현
  - 요구사항 ID: R-ACCESS-03, R-ORDER-07, R-ORDER-08, R-ORDER-09, R-ORDER-10, R-ORDER-11, R-ORDER-12, P-ORDER-04, ADR-004
  - 관찰한 Red와 실패 원인: `OrderConfirmService.confirm`이 비어 있어 성공 시 어떤 상태도 바뀌지 않고 모든 거절 시나리오도 예외를 내지 않는다.
  - 필요한 최소 동작: 소유권을 가장 먼저, 이어 확정 여부·상품 가용성·재고·포인트를 실제 변경 없이 검증한 뒤에만 모든 상품 재고와 구매자 포인트를 차감하고 주문을 확정한다. 주문 품목은 productId로 상품과 대응한다.
  - 변경할 production 파일: `order/domain/OrderConfirmService.java` (필요한 조회성 최소 메서드가 있으면 기존 domain 객체에만 추가)
  - 선행 작업: 1, 3, 5, 6번
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.order.domain.OrderConfirmServiceTest'`
  - 완료 조건: `OrderConfirmServiceTest`가 모두 Green이고 각 거절에서 주문·재고·포인트가 전부 유지된다.

## 최종 검증

- [x] 관련 테스트 전체 Green
- [x] 전체 테스트 Green
- [x] 테스트 파일 목록과 해시가 시작 시점과 동일
- [x] Checkstyle 통과
- [x] ArchitectureTest 통과
- [x] production diff 확인
