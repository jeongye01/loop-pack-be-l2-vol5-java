---
name: test-scenario-write
description: 요구사항을 테스트 파일에 배치하고 테스트 코드로 시나리오를 작성할 때 사용한다. docs/week2/requirements.md 의 요구사항을 알맞은 테스트 파일에 위치시키고, 요구사항마다 경계값 분석·동등 클래스 분할·의사결정표·상태 전이·오류 추측 중 알맞은 테스트 기법을 매핑해 테스트 코드를 작성한다. TDD 의 Red 단계에서 StockTest·OrderTest 같은 테스트를 쓸 때 쓴다.
---

# test-scenario-write

## 할 일

1. `docs/week2/requirements.md`의 요구사항(`R-…`)과 정책(`P-…`)을 알맞은 테스트 파일에 위치시킨다. 배치 결과는 `docs/week2/test-plan.md`의 `## 요구사항 배치` 표에 둔다.
2. 요구사항 하나를 골라 아래 테스트 기법을 알맞게 매핑하고, 그 기법으로 테스트 코드를 작성한다. 테스트 파일은 `src/test/java`에서 대상과 같은 패키지에 `<대상>Test.java`로 둔다.
3. 테스트를 작성한 뒤 [Red 검증](./references/red-validation.md)을 수행한다. 검증을 통과하기 전에는 Green 구현을 시작하지 않는다.

## 테스트 기법

| 기법 | 이런 요구사항에 | 테스트할 값 |
| --- | --- | --- |
| 경계값 분석 | 범위가 있다 | 최소값, 최대값, 최소 바로 아래, 최대 바로 위, 명목값(선택) |
| 동등 클래스 분할 | 허용되는 입력과 거절되는 입력이 나뉜다 | 유효한 클래스와 유효하지 않은 클래스에서 대표값 하나씩 |
| 의사결정표 | 결과가 조건 여럿의 조합으로 정해진다 | 조건의 모든 조합 |
| 상태 전이 | 상태에 따라 같은 요청의 결과가 다르다 | 허용되는 전이와 막히는 전이 |
| 오류 추측 | 경험상 잘 깨지는 곳이 있다 | 필수값 비움, 잘못된 형식, 예상치 못한 행동 |

## 테스트 코드 모양

- 요구사항 하나는 `@Nested` 하나다. `@DisplayName`에 요구사항 ID와 문장을 적는다.
- 테스트 하나는 시나리오 하나다. `@DisplayName` 앞에 기법 이름을 적는다.
- 거절은 오류 코드와 상태가 그대로인지 함께 확인한다. 도메인 테스트는 HTTP 상태를 확인하지 않는다.

## 단계 경계

이 스킬은 **Red 전용**이다.

- 요구사항에 근거한 테스트를 작성하고, 테스트가 의도한 미구현 동작 때문에 실패하는지 확인한다.
- 테스트를 통과시키기 위한 production 규칙은 구현하지 않는다.
- Red 검증이 끝나면 테스트 파일, 근거 ID, 실행 명령, 통과·실패 수와 실패 원인만 남기고 종료한다.
- Green은 이전 대화 문맥을 공유하지 않는 별도 에이전트에서 `test-green-implement` 스킬로 수행한다.

```java
class StockTest {

    @DisplayName("[R-ORDER-08] 주문을 확정하려면 각 상품의 재고가 주문 수량 이상이어야 한다.")
    @Nested
    class DecreaseWithinStock {

        @DisplayName("[경계값 분석] 재고 5에서 4, 5를 차감하면 1, 0이 남는다.")
        @ParameterizedTest
        @CsvSource({"4, 1", "5, 0"})
        void decreases_whenAmountIsWithinStock(int amount, int expected) {
            // arrange
            Stock stock = new Stock(5);

            // act
            Stock result = stock.decrease(amount);

            // assert
            assertThat(result.quantity()).isEqualTo(expected);
        }

        @DisplayName("[경계값 분석] 재고 5에서 6을 차감하면 재고 부족으로 거절하고, 재고는 5 그대로다.")
        @Test
        void throwsInsufficientStock_whenAmountExceedsStock() {
            // arrange
            Stock stock = new Stock(5);

            // act
            CoreException result = assertThrows(CoreException.class, () -> stock.decrease(6));

            // assert
            assertAll(
                () -> assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK),
                () -> assertThat(stock.quantity()).isEqualTo(5)
            );
        }
    }
}
```
