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

## Application · JPA 통합 Red 50개 (687c69b)

- [x] 8. 사용자 JPA 저장소와 포인트 유스케이스 구현 (6개 Red)
  - 요구사항 ID: R-POINT-01, R-POINT-02, R-POINT-06, R-POINT-08, P-POINT-01, ADR-006
  - 관찰한 실패: `PointUseCaseIntegrationTest` 5개와 `UserRepositoryIntegrationTest` 1개가 모두 `UnsupportedOperationException`으로 실패한다. `UserRepositoryAdapter`가 Spring Data 저장소를 보관·호출하지 않고, `PointUseCase`도 사용자 조회·충전·저장을 하지 않는다.
  - 필요한 최소 동작: JPA adapter가 `save/findById`를 위임하고, 유스케이스가 사용자를 조회하여 충전 후 저장·잔액 반환 및 저장 잔액 조회를 수행한다. 없는 사용자는 `USER_NOT_IDENTIFIED`로 거절한다.
  - 변경할 production 파일: `user/infrastructure/UserRepositoryAdapter.java`, `user/application/PointUseCase.java`
  - 선행 작업: 기존 완료 TODO 1, 5의 `Point`, `User`; Spring Data `UserJpaRepository`
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.user.application.PointUseCaseIntegrationTest' --tests 'com.loopers.user.infrastructure.UserRepositoryIntegrationTest'`
  - 완료 조건: 사용자/포인트 통합 테스트 6개가 Green이고 flush·clear 뒤 충전 잔액과 최초 잔액 0이 재조회되며 거절 시 기존 잔액이 유지된다.

- [x] 9. 브랜드 JPA 저장소 구현 (1개 Red)
  - 요구사항 ID: R-ADMIN-14, ADR-001, ADR-006
  - 관찰한 실패: `BrandRepositoryIntegrationTest` 1개가 `BrandRepositoryAdapter.save`의 `UnsupportedOperationException`으로 실패한다.
  - 필요한 최소 동작: adapter가 Spring Data 저장소를 보관하고 저장·식별자 조회·이름 조회·최신순 페이지 조회를 위임하여 논리 삭제 상태를 그대로 영속화한다.
  - 변경할 production 파일: `brand/infrastructure/BrandJpaRepository.java`, `brand/infrastructure/BrandRepositoryAdapter.java`
  - 선행 작업: 기존 완료 TODO 2의 JPA Entity `Brand`
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.brand.infrastructure.BrandRepositoryIntegrationTest'`
  - 완료 조건: 저장 후 논리 삭제한 브랜드를 flush·clear 뒤 포트로 재조회했을 때 삭제 상태가 유지되어 테스트 1개가 Green이다.

- [x] 10. 상품 JPA 조회·정렬·페이지 구현 (6개 Red)
  - 요구사항 ID: R-ADMIN-12, R-CATALOG-04, R-CATALOG-05, R-CATALOG-06, P-CATALOG-02, P-CATALOG-04, ADR-006
  - 관찰한 실패: `ProductRepositoryIntegrationTest` 6개가 adapter의 미구현 예외로 실패한다. 고객 목록에는 활성 상품 필터, 세 정렬, ID 보조 정렬과 페이지가 필요하다.
  - 필요한 최소 동작: 기본 CRUD/브랜드·이름 조건 조회를 Spring Data에 위임하고, 고객 조회는 삭제되지 않은 상품을 대상으로 `LATEST(createdAt desc)`, `PRICE_ASC(price asc)`, `LIKES_DESC(관계 count desc)` 뒤 `id asc`를 적용해 0 기반 페이지를 반환한다. 브랜드 가용성은 application이 검사하고, 관리자 목록은 삭제 여부와 관계없이 최신순·ID 오름차순으로 조회한다.
  - 변경할 production 파일: `product/infrastructure/ProductJpaRepository.java`, `product/infrastructure/ProductRepositoryAdapter.java`, `modules/jpa/.../BaseEntity.java`(flush·clear 전후 Entity 식별자 동등성)
  - 선행 작업: 9번 브랜드 영속 매핑, 기존 완료 TODO 3의 `Product`
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.product.infrastructure.ProductRepositoryIntegrationTest'`
  - 완료 조건: 상품 repository 통합 테스트 6개가 Green이고 flush·clear 이후 삭제 제외, 브랜드 필터, 세 정렬, 안정적인 페이지, 생성 시점 최신순이 모두 유지된다.

- [x] 11. 좋아요 JPA 관계 저장·집계·활성 상품 목록 구현 (2개 Red)
  - 요구사항 ID: R-LIKE-05, R-LIKE-07, ADR-006
  - 관찰한 실패: `LikeRepositoryIntegrationTest` 2개가 adapter의 미구현 예외로 실패한다.
  - 필요한 최소 동작: 동일 사용자·상품 관계 조회, 저장·삭제, 상품별 관계 수 집계를 위임하고, 내 좋아요는 삭제되지 않은 상품과 연결된 관계만 좋아요 생성 최신순·ID 오름차순으로 페이지 조회한다. 관계 자체는 상품 삭제 시 남긴다.
  - 변경할 production 파일: `like/infrastructure/LikeJpaRepository.java`, `like/infrastructure/LikeRepositoryAdapter.java`
  - 선행 작업: 10번 상품 JPA 조회
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.like.infrastructure.LikeRepositoryIntegrationTest'`
  - 완료 조건: 좋아요 repository 통합 테스트 2개가 Green이고 flush·clear 후 집계는 2이며 삭제 상품 관계는 DB에 남되 내 목록에서는 제외된다.

- [x] 12. 주문 JPA 컬렉션 저장과 최신순 조회 구현 (3개 Red)
  - 요구사항 ID: R-ADMIN-14, P-ORDER-07, P-ORDER-09, ADR-006
  - 관찰한 실패: `OrderRepositoryIntegrationTest` 3개가 adapter의 미구현 예외로 실패한다.
  - 필요한 최소 동작: `Order`와 `OrderItem` 값 컬렉션을 JPA로 저장·재조회하고, 구매자 목록과 관리자 선택 필터 목록을 생성 시점 내림차순·ID 오름차순으로 페이지 조회한다.
  - 변경할 production 파일: `order/infrastructure/OrderJpaRepository.java`, `order/infrastructure/OrderRepositoryAdapter.java`
  - 선행 작업: 기존 완료 TODO 6의 `Order`, `OrderItem`, `PaymentResult` JPA 매핑
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.order.infrastructure.OrderRepositoryIntegrationTest'`
  - 완료 조건: 주문 repository 통합 테스트 3개가 Green이고 상품 수정·삭제 뒤에도 주문 품목 스냅샷이 유지되며 최신 주문이 첫 페이지에 나온다.

- [x] 13. 브랜드 application CRUD와 정책 조합 구현 (5개 Red)
  - 요구사항 ID: R-ADMIN-01, R-ADMIN-02, R-ADMIN-15, P-ADMIN-01, P-ADMIN-06
  - 관찰한 실패: `BrandUseCaseIntegrationTest` 5개가 `BrandUseCase`의 미구현 예외로 실패한다.
  - 필요한 최소 동작: 포트로 CRUD/페이지 조회를 수행하고, 생성·수정 전 정규화된 이름 중복을 검사하며, 삭제 전 활성 여부와 연결된 활성 상품 여부를 검사한다. 변경 메서드는 한 트랜잭션에서 동작한다.
  - 변경할 production 파일: `brand/application/BrandUseCase.java`
  - 선행 작업: 9, 10번 repository adapter; 기존 완료 TODO 2의 validator
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.brand.application.BrandUseCaseIntegrationTest'`
  - 완료 조건: 브랜드 application 통합 테스트 5개가 Green이고 CRUD 상태가 저장되며 중복·연결 상품·재삭제 거절 때 DB 상태가 유지된다.

- [x] 14. 상품 application CRUD·재고·고객 조회 조합 구현 (10개 Red)
  - 요구사항 ID: R-ADMIN-04, R-ADMIN-05, R-ADMIN-08, R-LIKE-05, P-ADMIN-02, P-ADMIN-05, P-CATALOG-03, P-CATALOG-08
  - 관찰한 실패: `ProductUseCaseIntegrationTest` 10개가 `ProductUseCase`의 미구현 예외로 실패한다.
  - 필요한 최소 동작: 활성 브랜드 확인 후 중복 이름을 검사해 생성하고, 상품 CRUD·재고 변경을 포트로 저장한다. 고객 목록은 정렬 null을 `LATEST`로 바꾸고 없는/삭제된 브랜드 필터에는 빈 목록을 반환하며 각 상품의 좋아요 관계 수를 조합한다.
  - 변경할 production 파일: `product/application/ProductUseCase.java`
  - 선행 작업: 9~11번 repository adapter; 기존 완료 TODO 3의 validator
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.product.application.ProductUseCaseIntegrationTest'`
  - 완료 조건: 상품 application 통합 테스트 10개가 Green이고 CRUD·재고·중복·브랜드 가용성·기본 최신순·좋아요 수 조합 결과가 DB 상태와 일치한다.

- [x] 15. 좋아요 application 멱등 등록·취소·내 목록 구현 (6개 Red)
  - 요구사항 ID: R-LIKE-02, R-LIKE-03, R-LIKE-06, R-LIKE-08, P-LIKE-01
  - 관찰한 실패: `LikeUseCaseIntegrationTest` 6개가 `LikeUseCase`의 미구현 예외로 실패한다.
  - 필요한 최소 동작: 사용자와 활성 상품을 확인하고 기존 관계면 저장하지 않는 멱등 등록, 상품 삭제 여부와 무관하게 소유 관계가 있으면 삭제하는 멱등 취소, 활성 상품에 대한 요청자 관계 목록과 관계 기반 count 조회를 구현한다.
  - 변경할 production 파일: `like/application/LikeUseCase.java`
  - 선행 작업: 8, 10, 11번 repository adapter; 기존 완료 TODO 4의 소유권·중복 규칙
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.like.application.LikeUseCaseIntegrationTest'`
  - 완료 조건: 좋아요 application 통합 테스트 6개가 Green이고 반복 요청은 관계 수를 바꾸지 않으며 삭제 상품 관계 취소와 요청자 전용 목록이 동작한다.

- [x] 16. 주문 application 생성·확정·고객/관리자 조회 구현 (11개 Red)
  - 요구사항 ID: R-ORDER-04, R-ORDER-05, R-ORDER-10, R-ORDER-11, R-ORDER-13, R-ADMIN-10, P-ADMIN-10
  - 관찰한 실패: `OrderUseCaseIntegrationTest` 11개가 `OrderUseCase`의 미구현 예외로 실패한다.
  - 필요한 최소 동작: 구매자와 활성 상품을 포트로 조회해 스냅샷 품목으로 DRAFT 주문만 저장한다. 확정은 주문·전체 상품·구매자를 조회해 기존 도메인 서비스를 호출한 뒤 주문·상품·사용자를 한 트랜잭션에서 저장한다. 고객 조회는 소유권을 숨김 오류로 확인하고, 관리자는 구매자 선택 필터와 전체 목록 및 상세를 조회한다.
  - 변경할 production 파일: `order/application/OrderUseCase.java`
  - 선행 작업: 8, 10, 12번 repository adapter; 기존 완료 TODO 6, 7의 주문·확정 규칙
  - 테스트 명령: `./gradlew :apps:commerce-api:test --tests 'com.loopers.order.application.OrderUseCaseIntegrationTest'`
  - 완료 조건: 주문 application 통합 테스트 11개가 Green이고 생성은 재고·포인트를 유지하며 확정 성공은 세 aggregate를 함께 저장하고 모든 거절은 세 저장 상태를 유지한다.

- [x] 17. application/JPA Red 50개와 전체 회귀 검증
  - 요구사항 ID: 위 8~16번 전체, ADR-006, 아키텍처 의존 방향
  - 관찰한 실패: 기준 실행에서 통합 Red 50개 중 50개가 실패했다.
  - 필요한 최소 동작: 8~16번 구현만으로 50개를 모두 Green으로 만들고 기존 domain·Example·오류 계약을 회귀시키지 않는다. 테스트·기대값·검사 규칙은 변경하지 않는다.
  - 변경할 production 파일: 8~16번에 열거한 application/infrastructure 파일만 해당하며 HTTP/controller는 제외한다.
  - 선행 작업: 8~16번 완료
  - 테스트 명령: 통합 10개 클래스 50개 대상 실행, `./gradlew :apps:commerce-api:test`, `./gradlew :apps:commerce-api:check`, `./gradlew :apps:commerce-api:test --tests 'com.loopers.architecture.ArchitectureTest'`
  - 완료 조건: 관련 50개·전체 테스트·Checkstyle·ArchitectureTest가 모두 Green이고, `687c69b` 대비 모든 `src/test` 파일 SHA-256이 동일하며 `git diff --check`가 통과한다.
