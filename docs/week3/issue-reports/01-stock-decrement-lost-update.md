# 재고 차감 시 갱신 유실 재현

상태: 테스트용 대조군에서 재현 완료

## 재현

MySQL에 재고 5를 저장하고 두 독립 트랜잭션이 잠금 없는 SELECT로 각각 5를 읽는다. 두 읽기가 끝난 뒤 각 트랜잭션이 읽어 둔 값에서 1을 뺀 **상수 4**를 저장하고 commit한다.

현상 재현 테스트: [StockLostUpdateControlTest](../../../apps/commerce-api/src/test/java/com/loopers/product/infrastructure/StockLostUpdateControlTest.java)

```bash
./gradlew :apps:commerce-api:test --tests '*StockLostUpdateControlTest'
```

## 결과


| 결과 | 정상 처리 | 대조군 관찰 |
| --- | ---: | ---: |
| 두 트랜잭션의 조회값 | 5·5 | 5·5 |
| 두 차감 후 재고 | 3개 | 4개 |


두 트랜잭션 모두 완료됐지만 `초기 재고 5 − 차감 2 = 최종 재고 3`이 성립하지 않는다. 대조군 테스트는 재고 4와 불변식 위반을 확인해 통과한다.

## 원인

Lost Update(갱신 유실). 두 트랜잭션이 같은 재고 5를 읽고 각각 4를 저장해 차감 한 번이 반영되지 않았다.

## 해결 방법



## 해결 후 검증
