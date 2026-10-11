# 좋아요순 정렬에 취소된 좋아요 포함

상태: 하드 삭제 전환 및 전체 검사 완료 · 프로젝트 기본 DB에서 실제 API 검증 완료

## 수정 전 현상

`GET /api/v1/products?sort=likes_desc`의 정렬에는 취소된 좋아요가 포함되지만, 응답의 `likeCount`는 취소된 좋아요를 제외한다. 표시된 좋아요 수가 적은 상품이 먼저 나올 수 있다.

## 기대 동작

취소되지 않은 좋아요 수 내림차순, 동점이면 상품 ID 오름차순으로 전체 후보를 정렬한 뒤 페이지를 선택한다.

## 재현 시나리오

같은 활성 브랜드에 상품 A·B만 준비하고 서로 다른 사용자로 다음 상태를 만든다.

| 상품 | 등록한 좋아요 | 취소한 좋아요 | 현재 좋아요 수 |
| --- | --- | --- | --- |
| A | 3 | 3 | 0 |
| B | 1 | 0 | 1 |

존재하는 사용자의 `X-USER-ID` 헤더로 다음 요청을 보낸다.

```http
GET /api/v1/products?brandId={브랜드ID}&sort=likes_desc&page=0&size=1
```

- 기대 결과: B가 첫 페이지에 나오고 `likeCount`는 1이다.
- 현재 코드상 예상 결과: A가 첫 페이지에 나오고 `likeCount`는 0이다. HTTP 테스트에서 B 대신 A가 반환되는 것을 확인했다.

## 수정 전 원인

[ProductJpaRepository.findCustomerProductsByLikes](../../../apps/commerce-api/src/main/java/com/loopers/product/infrastructure/ProductJpaRepository.java)는 좋아요를 조인할 때 `l.deletedAt is null` 조건 없이 `count(l)`로 정렬한다.

반면 응답 집계는 당시 [LikeJpaRepository](../../../apps/commerce-api/src/main/java/com/loopers/like/infrastructure/LikeJpaRepository.java)의 `countByProductIdAndDeletedAtIsNull`을 사용해 취소된 좋아요를 제외했다.

## 해결 계획

좋아요 취소를 소프트 삭제에서 하드 삭제로 변경한다. 좋아요는 현재 사용자와 상품의 관계를 나타내며, 취소 이력을 보존할 요구가 없으므로 취소된 행을 남길 필요가 없다.

- 취소 시 해당 사용자·상품의 관계 행을 실제로 삭제한다. 다시 등록하면 새 관계를 생성한다.
- 중복 등록·반복 취소의 멱등성과 다른 사용자의 관계 보호는 유지한다.
- 상품 삭제만으로 기존 좋아요를 지우지는 않는다. 삭제된 상품에 남은 자신의 좋아요도 취소할 수 있다.
- 정렬과 응답 집계는 남아 있는 좋아요 관계를 기준으로 한다. 기존 DB의 소프트 삭제된 행은 전환 시 정리해야 한다.
- 상품별 반복 COUNT 문제는 하드 삭제로 해결되지 않으므로 별도 조회 개선 대상으로 남긴다.

## 검증 계획

- 취소 후 DB에 관계 행이 남지 않는지 확인한다.
- 취소 후 재등록하면 기존 행 복원 대신 새 ID의 관계가 생성되는지 확인한다.
- 반복 취소·상품 삭제 후 취소·다른 관계 보호를 확인한다.
- 위 재현 시나리오를 HTTP 테스트로 검증해 좋아요순 정렬·페이지와 응답 수가 일치하는지 확인한다.

구현 변경 전 테스트를 먼저 작성한다. 기존 소프트 삭제를 요구하던 통합 테스트는 합의한 하드 삭제 동작으로 변경한다.

## 테스트 결과

- [LikeUseCaseIntegrationTest](../../../apps/commerce-api/src/test/java/com/loopers/like/application/LikeUseCaseIntegrationTest.java): 행 삭제·새 관계 생성·반복 취소·상품 삭제 후 취소·다른 관계 보호 검증.
- [LikeHttpTest](../../../apps/commerce-api/src/test/java/com/loopers/like/interfaces/LikeHttpTest.java): 좋아요 취소 전후 정렬과 페이지 검증.

```bash
./gradlew :apps:commerce-api:test --tests '*LikeUseCaseIntegrationTest*' --tests '*LikeHttpTest*' --tests '*LikeTest*' --tests '*LikeRepositoryIntegrationTest*' --tests '*ArchitectureTest'
./gradlew :apps:commerce-api:checkstyleMain :apps:commerce-api:checkstyleTest
```

2026-10-11 구현 전 실행: 30개 중 24개 통과, 6개 실패, skip 0개. 실패는 관계 행 잔존·기존 ID 복원 5개와 취소 후 좋아요순 정렬 1개로, 당시 소프트 삭제 구현에서 발생했다. ArchUnit과 Checkstyle은 통과했다.

## 구현 결과

- `LikeUseCase.cancel`은 소유권을 확인한 관계를 `LikeRepository.delete`로 삭제한다. 삭제는 현재 트랜잭션 안에서 실행되므로 rollback 대상이다.
- 재등록은 복원하지 않고 새 관계를 만든다. 삭제 직후 같은 트랜잭션에서 재등록하더라도 유니크 제약에 걸리지 않도록 삭제를 flush한다.
- 좋아요 수와 내 좋아요 목록은 남아 있는 관계를 조회한다. 상품 삭제 조건은 유지한다.
- 기존 `Like`의 취소 상태·소유권 메서드는 유지하되, 취소 상태를 저장하는 대신 관계를 삭제한다. 애플리케이션은 `reregister`를 사용하지 않는다.

승인된 테스트 파일은 변경하지 않았다. 좋아요·상품·ArchUnit·도메인 문서 검사를 포함한 관련 테스트 147개가 모두 통과했다.

2026-10-11 `./gradlew :apps:commerce-api:check` 통과: 전체 테스트 358개, 실패·오류·skip 0개. Checkstyle과 ArchUnit도 통과했다. week2 문서는 변경하지 않고 이 문서에 전환 결정을 기록한다.

### 기존 DB 전환

[전환 SQL](../migrations/01-hard-delete-canceled-likes.sql)은 기존 `product_like`에서 `deleted_at IS NOT NULL`인 행만 삭제한다. 활성 관계와 삭제된 상품에 남은 활성 좋아요는 보존한다.

기존 데이터를 유지하는 환경에서는 해당 DB를 선택하고 애플리케이션 읽기·쓰기를 중단한 상태에서 SQL을 적용한 뒤 새 코드를 실행한다. 정리하지 않으면 과거 취소 행도 현재 관계로 집계될 수 있다. 이번 작업에서는 실제 DB에 전환 SQL을 실행하지 않았다.

2026-10-11 기본 DB(`localhost:3306/loopers`)는 빈 상태였으므로 전환 SQL 없이 현재 앱의 `local` 프로필로 테이블을 생성했다. 실제 API에서 등록 `201` → 취소 `200` → 관계 행 수 0, 반복 취소 `200`, 재등록 `201`과 새 관계 ID를 확인했다. 검증 데이터는 모두 정리했고 소프트 삭제된 좋아요 행은 0개다. `deleted_at` 컬럼은 공통 `BaseEntity` 매핑으로 남지만 취소 처리는 실제 DELETE다.
