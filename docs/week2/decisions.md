# commerce-api 의사결정 기록

설계에서 대안을 비교해 내린 결정과 그 이유다. 근거는 [요구사항 문서](./requirements.md)의 요구사항 ID와 정책 ID를 가리킨다.

## ADR-001. 브랜드·상품은 논리 삭제한다

- 상태: 결정 (2026-09-17)
- 근거: `R-ADMIN-12`, `R-ADMIN-13`, `R-ADMIN-14`, `R-LIKE-08`

**상황**: 삭제된 브랜드·상품은 고객 조회와 새 주문에서 빠지고, 수정과 재고 변경을 할 수 없다. 삭제해도 기존 참조와 저장된 주문 정보는 남아야 하고, 삭제된 상품에 남은 자신의 좋아요는 취소할 수 있어야 한다.

**대안**

- A. 물리 삭제: 기록을 지운다.
- B. 논리 삭제: 기록은 두고 삭제 여부만 남긴다.

**결정**: B. 논리 삭제한다.

- 기록을 지우면 상품을 가리키는 좋아요와 주문 품목의 참조가 깨진다.
- 삭제 여부를 Brand와 Product가 상태로 가지므로, "삭제된 대상은 수정할 수 없다"를 객체가 스스로 지킨다.

**논리 삭제로 생기는 문제와 대응**

| 문제 | 대응 |
| --- | --- |
| 삭제된 브랜드·상품의 이름이 남아서, 같은 이름으로 다시 만들 수 없게 된다. | 이름 중복은 삭제되지 않은 대상끼리만 비교한다(`P-ADMIN-01`, `P-ADMIN-02`). |
| "삭제된 것은 보이지 않게"를 모든 조회에 한꺼번에 적용하면 관리자 조회와 주문 조회에서도 사라진다. | 삭제된 대상을 거르는 곳을 고객 조회와 새 주문으로 한정한다(`R-ADMIN-12`, `P-ADMIN-07`). 주문은 저장된 당시 값으로 보여 준다(`P-ORDER-07`). |

**다시 검토할 조건**: 삭제한 이름을 다시 쓰지 못하게 해야 할 때, 또는 삭제한 데이터를 실제로 지워야 하는 요구가 생길 때

## ADR-002. 오류 코드는 HTTP를 모르는 enum 하나에 둔다

- 상태: 결정 (2026-09-17)
- 근거: [아키텍처의 의존 방향](./commerce-api-design.md#의존-방향), [API 응답 계약의 오류 코드](./api-response-contract.md#오류-코드)

**상황**: starter의 `CoreException`은 `ErrorType`을 담는다. `ErrorType`은 HTTP 상태(`HttpStatus`)를 가지고, 응답의 `meta.errorCode`로 상태 문구(`Bad Request`, `Not Found`)를 쓴다. 이대로 쓰면 다음 문제가 생긴다.

- domain이 예외를 던질 때 HTTP 상태를 고른다. 불변식 로직이 외부 기술을 알게 된다.
- 코드가 상태마다 하나라서, API 응답 계약의 규칙별 코드를 담을 수 없다. 재고 부족과 잔액 부족이 같은 코드로 나간다.
- 도메인 테스트가 거절을 HTTP 상태로만 확인해서, 어떤 규칙으로 거절했는지 구분하지 못한다.

과제는 starter의 Example 테스트와 오류 계약 정리 방식을 참고하라고 하고, 바꾸지 말라고 하지 않는다.

**대안**

- A. starter 그대로 `ErrorType`에 상태별 코드를 둔다.
- B. HTTP를 모르는 오류 코드를 기능별 enum으로 나누고, 공통 인터페이스로 묶는다.
- C. HTTP를 모르는 오류 코드를 enum 하나에 둔다.

**결정**: C. `ErrorCode` enum 하나에 두고, `CoreException`이 `ErrorType` 대신 `ErrorCode`를 담는다.

- domain과 application은 HTTP를 모른다. 상태는 interfaces의 `ErrorStatus`가 정한다.
- 코드가 규칙마다 하나라서, 도메인 테스트가 거절한 규칙을 코드로 확인한다.
- enum 하나가 API 응답 계약의 오류 코드 표와 1:1로 맞는다.
- `PRODUCT_NOT_FOUND`처럼 여러 기능이 함께 쓰는 코드가 있다. 기능별로 나누면 주문·좋아요가 상품의 오류 코드 enum을 알아야 한다.
- 예외는 starter의 `CoreException`을 그대로 쓴다. 새 예외를 만들면 두 예외가 함께 남는다.

| 바꾼 것 | 전 | 후 |
| --- | --- | --- |
| 오류 코드 | `ErrorType`(상태, 상태 문구, 메시지) | `ErrorCode`(메시지). 이름이 `meta.errorCode`가 된다. |
| 예외 | `CoreException(ErrorType)` | `CoreException(ErrorCode)` |
| 상태를 정하는 곳 | `ErrorType` | interfaces의 `ErrorStatus` |

**C로 생기는 문제와 대응**

| 문제 | 대응 |
| --- | --- |
| 코드를 추가할 때 상태 매핑을 빠뜨릴 수 있다. | `ErrorStatus`는 모든 코드를 다루는 `switch`라서 빠뜨리면 컴파일이 실패한다. `ErrorStatusTest`가 계약 표의 상태와 비교한다. |
| Example API의 오류 응답이 바뀐다. | `meta.errorCode`가 `Bad Request`에서 `INVALID_REQUEST`로, `Not Found`에서 `NOT_FOUND`로 바뀌었고, 테스트의 기대값을 함께 고쳤다. |
| 여러 기능이 한 파일을 함께 고친다. | 코드가 26개라서 한 파일로 둔다. 아래 조건이 되면 B로 나눈다. |

**다시 검토할 조건**: 기능과 코드가 늘어 여러 기능이 한 파일을 자주 함께 고치게 될 때

## ADR-003. 재고 차감 수량이 0 이하이면 내부 오류로 거절한다

- 상태: 결정 (2026-09-17)
- 근거: `R-ORDER-06`, `R-ORDER-11`, `R-ADMIN-08`

**상황**: 재고는 주문 확정에서만 주문 수량만큼 줄어든다(`R-ORDER-11`). 주문 수량은 양수여야 하고(`R-ORDER-06`), 관리자의 재고 변경은 빼는 것이 아니라 최종 수량을 정하는 것이다(`R-ADMIN-08`). 그래서 어떤 요청으로도 0 이하의 수량이 재고 차감까지 오지 않는다. 요구사항은 Stock이 0 이하의 차감을 받았을 때를 정하지 않는다.

사용자가 겪는 제품 동작이 아니라 객체를 잘못 호출했을 때의 문제라서, 정책 확인 사항에 두지 않고 설계로 정한다.

**대안**

- A. 확인하지 않는다. 호출하는 쪽이 양수만 넘긴다고 믿는다.
- B. 주문 수량 오류(`INVALID_ORDER_QUANTITY`)로 거절한다.
- C. 내부 오류(`INTERNAL_ERROR`)로 거절한다.

**결정**: C. 0 이하의 차감은 `INTERNAL_ERROR`로 거절하고, 재고는 그대로 둔다.

- A는 `-3`을 차감하면 재고가 3 늘어난다. 늘어난 재고도 0 이상이라서 "수량은 0 이상" 규칙으로는 걸러지지 않는다.
- B는 주문의 규칙이다. Stock이 이 코드를 쓰면 재고가 주문을 알게 된다.
- 요청으로는 생길 수 없으므로, [API 응답 계약](./api-response-contract.md#오류-코드)의 "요청과 관계없는 내부 오류"에 해당한다.
- 0은 재고를 바꾸지 않지만, 주문 수량이 양수라는 규칙(`R-ORDER-06`)과 기준을 맞춰 함께 거절한다.

**C로 생기는 문제와 대응**

| 문제 | 대응 |
| --- | --- |
| 이 오류가 나면 요청자는 원인을 알 수 없는 `500`을 받는다. | 요청으로는 생기지 않으므로, 이 오류는 호출하는 코드의 결함이다. 주문 수량이 양수인지는 주문에서 확인하고 테스트한다(`R-ORDER-06`). |

**다시 검토할 조건**: 주문 확정 말고도 요청으로 재고를 빼는 기능이 생길 때

## ADR-004. 주문 확정에서 여러 조건이 함께 실패하면 먼저 확인한 조건의 오류를 준다

- 상태: 결정 (2026-09-17)
- 근거: `R-ORDER-07`, `R-ORDER-08`, `R-ORDER-09`, `R-ORDER-10`, `P-ACCESS-02`, `P-ORDER-04`

**상황**: 주문 확정은 본인의 주문인지, `DRAFT`인지, 상품이 삭제되지 않았는지, 재고와 잔액이 충분한지 확인한다. 둘 이상이 함께 실패해도 응답에는 오류 코드를 하나만 담는다. 요구사항은 조건마다 거절한다고만 하고, 함께 실패할 때 어느 오류를 알릴지 정하지 않는다.

**대안**

- A. 조건 사이의 우선순위를 정책으로 정하고, 조합마다 테스트한다.
- B. 확인하는 순서대로 처리하고, 먼저 실패한 조건의 오류를 준다.
- C. 실패한 조건을 모두 모아 알린다.

**결정**: B. 단, 본인의 주문인지는 가장 먼저 확인한다.

- 어느 오류를 주든 주문·재고·잔액은 그대로라서 저장된 데이터가 달라지지 않는다. 나중에 순서를 바꿔도 응답만 달라진다.
- A는 요구사항이 정하지 않은 조합을 정책으로 만든다.
- C는 [API 응답 계약](./api-response-contract.md#응답-형식)이 오류 코드를 하나만 담으므로 응답 형식을 바꿔야 한다.
- 본인 확인을 뒤에 두면 남의 확정된 주문에 `ORDER_ALREADY_CONFIRMED`가 나가서 그 주문이 있다는 사실이 드러난다(`P-ACCESS-02`). 그래서 본인 확인만 순서를 고정한다.

**테스트**: 조건은 하나씩 실패시켜 확인한다. 조합은 남의 주문이 다른 조건과 함께 실패해도 `ORDER_NOT_FOUND`인지만 확인한다.

**다시 검토할 조건**: 함께 실패한 조건을 모두 알려 달라는 요구나, 특정 오류를 먼저 알려야 하는 요구가 생길 때

## ADR-005. Red와 Green은 컨텍스트를 공유하지 않는 별도 에이전트가 수행한다

- 상태: 결정 (2026-09-17)
- 근거: [테스트 계획](./test-plan.md), 프로젝트 `AGENTS.md`, `test-scenario-write`, `test-green-implement`

**상황**: 한 에이전트가 요구사항을 해석해 Red 테스트를 작성하고 곧바로 Green 구현까지 하면, 테스트를 작성할 때 떠올린 구현 구조와 암묵적인 가정을 그대로 구현에 가져갈 수 있다. 이 경우 테스트가 저장소에 남은 실행 가능한 계약만으로 충분한지 확인하기 어렵고, Red 단계에서 테스트를 통과시키기 위한 production 규칙까지 미리 구현할 위험이 있다.

**대안**

- A. 한 에이전트가 같은 대화 문맥에서 Red, Green, Refactor를 연속 수행한다.
- B. Red와 Green은 별도 에이전트가 수행하되, Red 세션의 대화 요약과 구현 아이디어를 Green에 전달한다.
- C. Red와 Green을 별도 에이전트가 수행하고, Green에는 저장소의 요구사항·테스트·현재 코드만 전달한다.

**결정**: C. Red와 Green은 컨텍스트를 공유하지 않는 별도 에이전트가 수행한다.

- Red 에이전트는 요구사항을 테스트 시나리오로 표현하고, 요구사항 밖 기대값이 없는지와 의도한 이유로 실패하는지를 검증한 뒤 종료한다.
- Green 에이전트는 새 컨텍스트에서 `AGENTS.md`, 요구사항, 테스트 계획, 설계·계약·ADR, Red 테스트, 현재 production 코드만 읽는다.
- 두 에이전트 사이의 계약은 저장소에 남은 파일과 테스트 실행 결과다. Red 작성자의 구현 아이디어나 예상 내부 구조는 전달하지 않는다.
- Green 에이전트는 테스트와 요구사항의 일치를 독립적으로 다시 확인하고, 충돌하면 production 코드를 작성하지 않고 보고한다.
- Green에서는 실패한 테스트를 통과시키는 최소 production 코드만 작성한다. 테스트·기대값·계약·검사 규칙은 삭제하거나 완화하지 않는다.
- Refactor는 관련 테스트가 모두 Green인 뒤에만 수행하고, 관련 테스트·전체 테스트·Checkstyle·ArchUnit으로 동작 유지 여부를 다시 확인한다.

**C로 생기는 문제와 대응**

| 문제 | 대응 |
| --- | --- |
| Green 에이전트가 Red 작성 과정의 판단을 알지 못한다. | 필요한 판단은 대화가 아니라 요구사항·API 계약·ADR·테스트에 남긴다. 저장소만 읽고 구현할 수 없다면 Red 인계가 불완전한 것으로 본다. |
| Red 테스트 자체가 잘못됐을 수 있다. | Green 시작 전에 요구사항 ID와 테스트 기대값을 독립적으로 다시 대조한다. 충돌하면 테스트를 임의로 고치지 않고 보고한다. |
| 에이전트를 나누어 확인 작업이 중복된다. | 중복 확인을 테스트가 독립적인 실행 계약인지 검증하는 비용으로 받아들인다. |

**다시 검토할 조건**: 독립 Green 에이전트가 저장소의 계약만으로 반복해서 구현할 수 없거나, 에이전트 분리 비용이 검증 효과보다 커질 때

## ADR-006. Repository에는 DIP를 적용하고 domain 객체를 JPA Entity로 사용한다

- 상태: 결정 (2026-09-17)
- 근거: [아키텍처의 의존 방향](./commerce-api-design.md#의존-방향), [테스트 계획](./test-plan.md), 과제의 `4. 실행·제출`

**상황**: application과 domain의 규칙은 DB 없이 빠르게 확인하고, repository의 저장·조회는 실제 JPA와 MySQL Testcontainers로 확인해야 한다. application이 Spring Data JPA나 구체 저장소를 직접 사용하면 규칙 테스트에도 DB가 필요하고, 저장 기술이 application으로 전파된다.

Repository 포트와 어댑터로 의존을 역전하는 것과 domain 객체에서 JPA annotation을 제거하는 것은 별개의 결정이다. domain 객체와 JPA Entity를 분리하면 domain은 영속 기술을 전혀 모르지만, 같은 상태를 가진 Entity와 변환 코드를 함께 관리해야 한다. 반대로 domain 객체를 JPA Entity로 사용하면 persistence metadata가 domain에 남지만 별도 모델과 변환 없이 JPA 변경 감지를 사용할 수 있다.

**대안**

- A. application이 Spring Data `JpaRepository`를 직접 사용한다.
- B. domain에 Repository 포트를 두고 infrastructure의 JPA 어댑터가 포트를 구현하며, domain 객체와 JPA Entity는 분리한다.
- C. domain에 Repository 포트를 두고 infrastructure의 JPA 어댑터가 포트를 구현하며, domain 객체를 JPA Entity로 함께 사용한다.

**결정**: C. Repository에는 DIP를 적용하고 domain 객체를 JPA Entity로 함께 사용한다.

```text
application ──▶ domain Repository(port) ◀── infrastructure JPA adapter ──▶ MySQL
```

- application은 domain의 Repository 포트에만 의존한다.
- Spring Data `JpaRepository`와 Repository 포트의 어댑터는 infrastructure에 둔다.
- domain 객체에는 영속 매핑을 위한 JPA annotation과 기본 생성자를 허용한다. domain 규칙은 Entity 안에 유지한다.
- domain은 Spring Data Repository, EntityManager, 구체 쿼리나 MySQL API를 직접 사용하지 않는다.
- 별도의 infrastructure JPA Entity와 domain 변환 mapper는 만들지 않는다.
- production에서는 Spring이 JPA 어댑터를 Repository 포트의 구현으로 주입한다.
- domain·application 테스트는 mock 또는 메모리 구현으로 포트를 대체해 DB 없이 실행한다.
- repository 통합 테스트는 JPA 어댑터와 MySQL 8.0 Testcontainers를 사용하고, 저장 후 `flush`·`clear`한 뒤 다시 조회한다.
- HTTP 테스트는 실제 controller·application·JPA 어댑터와 테스트 DB를 연결한다.
- 테스트와 로컬 실행의 DB 동작 차이를 만들지 않기 위해 H2를 추가하지 않는다.

**C로 생기는 문제와 대응**

| 문제 | 대응 |
| --- | --- |
| domain이 JPA annotation과 `BaseEntity`에 컴파일 시점 의존한다. | 영속 metadata와 기본 생성자만 허용하고, 저장소·쿼리·트랜잭션 같은 실행 기술은 infrastructure와 application에 둔다. |
| JPA 매핑 변경이 domain 클래스의 변경을 일으킨다. | 이번 과제의 단순 aggregate에서는 별도 Entity와 mapper의 중복 비용이 더 크다고 판단한다. 영속 모델과 domain 모델의 변화 속도가 달라지면 분리를 다시 검토한다. |
| mock·메모리 구현만으로는 실제 매핑과 쿼리 오류를 찾을 수 없다. | repository·DB 경계는 MySQL Testcontainers 통합 테스트로 별도 확인한다. |
| 테스트를 위한 구조가 production 코드에도 남는다. | 테스트 전용 분기가 아니라 의존 방향을 지키는 production 구조로 사용하며, 테스트에서는 같은 포트를 교체 가능 지점으로 활용한다. |

**다시 검토할 조건**: JPA 제약 때문에 domain 규칙 표현이 어려워지거나, 영속 모델과 domain 모델의 변화 속도가 달라지거나, JPA 외 저장 기술을 함께 사용하게 될 때
