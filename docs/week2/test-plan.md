# commerce-api 테스트 계획

## 목표

[요구사항 문서](./requirements.md)의 요구사항(`R-…`)과 정책 판단(`P-…`)이 지켜지는지 확인한다.

## 검증 기준

- 요구사항마다 성공하는 경우와 거절되는 경우를 확인한다. 기대값은 [API 계약](./api-contract.md)을 따른다.
- 거절되면 오류 코드와 함께 저장된 값이 그대로인지 확인한다.
- 요구사항 ID는 `@Nested`의 `@DisplayName`에, 고른 기법은 테스트의 `@DisplayName`에 적는다.
- 모든 ID가 테스트 이름에 있으면 검증을 마친 것이다.

## 확인하는 곳

요구사항은 그것을 판단하는 곳에서 확인한다. 테스트는 `src/test/java`에서 대상과 같은 패키지에 `<대상>Test`로 둔다.

| 요구사항 | 테스트 | 도구 |
| --- | --- | --- |
| 값과 상태의 규칙 (범위, 차감, 확정 조건) | 도메인 단위 | JUnit. Spring과 DB 없이 실행한다. |
| 요청의 처리 결과 (없는 대상, 남의 대상, 반복 요청, 여러 대상을 함께 저장, 거절 시 저장된 값 유지) | 유스케이스 통합 | `@SpringBootTest`, MySQL 테스트 컨테이너 |
| 저장한 뒤의 조회 (삭제된 대상 제외, 정렬, 페이지, 좋아요 수) | 리포지토리 통합 | 같은 도구. 저장 → `flush`·`clear` → 다시 조회 |
| 요청자 식별, 응답 필드, 상태 코드 | HTTP | MockMvc |

## 경우 고르기

요구사항마다 `test-scenario-write` 스킬의 기법(경계값 분석, 동등 클래스 분할, 의사결정표, 상태 전이, 오류 추측) 중 맞는 것으로 확인할 경우를 고른다.

## 요구사항 배치

도메인 밖의 테스트 파일 이름은 구현할 때 정한다.

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
| `brand/application/BrandUseCaseIntegrationTest`, `brand/infrastructure/BrandRepositoryIntegrationTest` | R-ADMIN-01, R-ADMIN-02, R-ADMIN-14, R-ADMIN-15, P-ADMIN-01, P-ADMIN-06 |
| `brand` HTTP | R-ADMIN-01, R-ADMIN-12, R-CATALOG-01, R-CATALOG-07, P-ADMIN-07, P-ADMIN-08 |
| `product/application/ProductUseCaseIntegrationTest`, `product/infrastructure/ProductRepositoryIntegrationTest` | R-ADMIN-04, R-ADMIN-05, R-ADMIN-08, R-ADMIN-12, R-CATALOG-04, R-CATALOG-05, R-CATALOG-06, R-LIKE-05, P-ADMIN-02, P-ADMIN-05, P-CATALOG-02, P-CATALOG-03, P-CATALOG-04, P-CATALOG-08 |
| `product` HTTP | R-ACCESS-06, R-ADMIN-04, R-ADMIN-06, R-ADMIN-08, R-ADMIN-09, R-ADMIN-13, R-CATALOG-02, R-CATALOG-03, R-CATALOG-07, R-CATALOG-08, P-ADMIN-03, P-ADMIN-04, P-ADMIN-07, P-ADMIN-08, P-ADMIN-09, P-CATALOG-01, P-CATALOG-05, P-CATALOG-06, P-CATALOG-07 |
| `like/application/LikeUseCaseIntegrationTest`, `like/infrastructure/LikeRepositoryIntegrationTest` | R-LIKE-02, R-LIKE-03, R-LIKE-05, R-LIKE-06, R-LIKE-07, R-LIKE-08, P-LIKE-01 |
| `like` HTTP | R-LIKE-01, R-LIKE-04, R-LIKE-06, P-ACCESS-02, P-LIKE-01, P-LIKE-02 |
| `user/application/PointUseCaseIntegrationTest`, `user/infrastructure/UserRepositoryIntegrationTest` | R-POINT-01, R-POINT-02, R-POINT-06, R-POINT-08, P-POINT-01 |
| `user` 포인트 HTTP | R-POINT-01, R-POINT-02, R-POINT-04, R-POINT-06, R-POINT-07, R-POINT-08 |
| `order/application/OrderUseCaseIntegrationTest`, `order/infrastructure/OrderRepositoryIntegrationTest` | R-ADMIN-10, R-ADMIN-14, R-ORDER-04, R-ORDER-05, R-ORDER-10, R-ORDER-11, R-ORDER-13, P-ADMIN-10, P-ORDER-07, P-ORDER-09 |
| `order` HTTP | R-ADMIN-10, R-ADMIN-11, R-ORDER-01, R-ORDER-05, R-ORDER-06, R-ORDER-13, R-ORDER-14, P-ACCESS-02, P-ADMIN-10, P-ORDER-01, P-ORDER-04, P-ORDER-07, P-ORDER-08, P-ORDER-09 |
| 연결 흐름 HTTP | R-POINT-02, R-POINT-06, R-ORDER-11, R-ORDER-12, R-ORDER-14 |

## 요구사항이 정하지 않아 확인하지 않는 것

| 내용 | 확인하는 방법 |
| --- | --- |
| 주문 확정에서 여러 조건이 함께 실패할 때 알리는 오류 | 요구사항이 정하지 않아, 먼저 확인한 조건의 오류를 준다. 확정 조건은 하나씩 실패시켜 확인한다. 단, 남의 주문은 다른 조건과 함께 실패해도 `ORDER_NOT_FOUND`인지 확인한다(P-ACCESS-02). ([ADR-004](./decisions.md#adr-004-주문-확정에서-여러-조건이-함께-실패하면-먼저-확인한-조건의-오류를-준다)) |
