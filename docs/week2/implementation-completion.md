# commerce-api 구현 마무리 기록

2026-09-18에 Green 구현 뒤 남아 있던 Refactor, 설계 대조, 검사 범위 연결을 마무리한 기록이다. 요구사항, Red 테스트, 검사 규칙은 수정하거나 완화하지 않았다.

## 변경한 책임과 파일

### 전체 Java 모듈 Checkstyle 연결

- 루트 [`build.gradle.kts`](../../build.gradle.kts)에서 모든 하위 모듈에 Checkstyle 10.26.1과 저장소 공통 규칙을 적용했다.
- 기존 코드에서 새 검사 범위에 걸린 두 import만 정리했다.
  - `modules/kafka/.../KafkaConfig.java`: 별표 import를 실제 사용하는 타입의 개별 import로 바꿨다.
  - `apps/commerce-batch/.../DemoJobE2ETest.java`: 사용하지 않는 import를 제거했다.
- Kafka 설정값, Bean 구성, 배치 테스트의 동작은 바꾸지 않았다.

### 대표 TDD 사례 Refactor

대표 규칙은 `Stock`의 재고 차감이다.

| 단계 | 커밋·변경 | 확인한 내용 |
| --- | --- | --- |
| Red | `04e1d73` | 재고 이내 차감, 재고 부족 거절, 성공·실패 뒤 원본 재고 유지 시나리오를 먼저 작성했다. |
| Green | `0f87e24` | `Stock.decrease`가 조건을 검사하고 새 `Stock`을 반환하는 최소 구현을 추가했다. |
| Refactor | 현재 변경 | 양수 조건과 재고 충분 조건을 `ensurePositive`, `ensureSufficient`로 분리해 차감 흐름과 거절 이유를 드러냈다. |

Red 테스트와 오류 코드는 바꾸지 않았다. Refactor 전후 모두 `Stock`은 불변이며 성공하면 새 값을 반환하고, 실패하면 기존 값을 유지한다.

### 설계와 구현 대조

- `interfaces → application → domain ← infrastructure` 의존 방향은 `ArchitectureTest`로 확인했다.
- 문서의 애그리거트와 구현을 대조했다.
  - 사용자: `User`가 `Point`를 가진다.
  - 상품: `Product`가 `Stock`을 가진다.
  - 주문: `Order`가 `OrderItem`과 `PaymentResult`를 가진다.
- 여러 애그리거트나 저장된 관계를 확인하는 도메인 서비스가 구현에 존재하는지 확인했다.
  - 브랜드 삭제 가능 여부, 브랜드·상품 이름 중복 여부, 좋아요 중복 여부, 주문 확정
- 좋아요 등록이 저장소를 직접 조회하던 부분을 설계에 적힌 `LikeDuplicationChecker`를 사용하도록 정리했다. 판단 결과와 API 동작은 바뀌지 않았다.
- 주문 품목 로딩 방식은 구현 중 생긴 판단이어서 [ADR-007](./decisions.md#adr-007-주문을-조회할-때-품목을-함께-로딩한다)에 대안과 비용을 기록했다.

## 실행한 검사

| 검사 | 결과 |
| --- | --- |
| `StockTest`, `ProductTest`, `LikeDuplicationCheckerTest`, `LikeUseCaseIntegrationTest` | 성공 |
| `./gradlew :apps:commerce-api:check --rerun-tasks` | 성공 |
| commerce-api 테스트 결과 | 449개, 실패 0, 오류 0, skip 0 |
| `ArchitectureTest` | 1개, 실패 0 |
| commerce-api main·test Checkstyle | 성공 |
| `./gradlew check --continue` | 성공, 전체 하위 모듈 Checkstyle 포함 |
| `git diff --check` | 성공 |

## 제출 설명에 사용할 요약

- 대표 TDD 사례: 재고 차감 규칙을 Red → Green → 조건 검증 메서드 분리 Refactor 순서로 진행했다.
- 설계에서 바뀐 판단: 주문 응답이 항상 품목을 사용하고 Open EntityManager in View를 끈 환경이므로 주문과 품목을 함께 로딩한다. 대안과 재검토 조건은 ADR-007에 남겼다.
- 검사: commerce-api 테스트 449개, Checkstyle, ArchUnit과 루트 전체 `check`가 통과했다.
