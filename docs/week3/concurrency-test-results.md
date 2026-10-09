# 주문 동시성 테스트 결과

## 현재 구현 검증 (2026-10-09)

[OrderConcurrencyTest](../../apps/commerce-api/src/test/java/com/loopers/order/application/OrderConcurrencyTest.java)를 실제 MySQL로 재실행해 4건 모두 통과했다. 각 요청은 시작 시점만 맞추고 독립된 트랜잭션에서 유스케이스를 호출한다. 모든 worker 종료 후 새 트랜잭션에서 DB를 재조회하며 성공·업무 거절·기술 오류와 재고·잔액·주문 상태·결제 결과를 함께 확인한다.

```bash
./gradlew :apps:commerce-api:test \
  --tests '*BrandRemovalTransactionTest' --tests '*OrderConcurrencyTest' \
  :apps:commerce-api:checkstyleTest
```

| 시나리오 | 관찰한 결과 | 결과 |
| --- | --- | --- |
| 재고 5개에 주문 8건 | 성공 5·재고 부족 3·기술 오류 0·최종 재고 0 | 통과 |
| 잔액 10,000원에 4,000원 주문 3건 | 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2,000원·최종 재고 8 | 통과 |
| 같은 주문 동시 확정 2건 | 성공 1·재확정 거절 1·기술 오류 0·재고와 포인트 차감 각 1회 | 통과 |
| 2,000원 충전과 7,000원 결제 | 두 요청 성공·기술 오류 0·최종 잔액 5,000원 | 통과 |

동일 실행에서 브랜드 삭제 실패 테스트 1건도 통과했다. 실제 변경을 flush한 뒤 저장 예외를 유발하고, 새 트랜잭션에서 삭제 대상의 rollback과 다른 브랜드·상품 및 기존 확정 주문의 품목·금액·결제 시각·재고·잔액 보존을 확인했다. Checkstyle도 통과했다.

이후 `./gradlew :apps:commerce-api:check`로 전체 회귀 테스트·Checkstyle·ArchUnit을 확인했고 `BUILD SUCCESSFUL`로 종료했다(3분 1초). 테스트 태스크는 재실행됐으며 현재 결과가 반영됐다.

### 확인 범위와 한계

- 위 결과는 현재 구현의 이번 실행 결과이며, 가능한 모든 스케줄에서 성공을 보장한다는 의미는 아니다.
- 주문 확정은 Point·Order 버전 충돌 시 전체 트랜잭션을 최대 2회 재시도한다. 포인트 충전 자체는 자동 재시도하지 않으므로 충전 쪽에 실제 버전 충돌이 발생하면 실패할 수 있다.
- 재시도를 소진한 포인트 충돌도 현재는 `ORDER_ALREADY_CONFIRMED`로 변환된다. 충돌 대상별 응답 구분은 별도 보완 항목이다.
- 포인트 경쟁 사례는 같은 상품의 Stock 배타락도 거치므로, 이 사례만으로 재고가 서로 다른 주문 사이의 Point 버전 충돌을 강제 재현했다고 볼 수는 없다.

## 개선 전 대조 기록 (2026-10-04)

아래는 개선 전 [OrderConcurrencyTest](../../apps/commerce-api/src/test/java/com/loopers/order/application/OrderConcurrencyTest.java)를 실제 MySQL로 10회 실행한 과거 기록이다. 현재 구현의 실패 결과와 구분한다.

```bash
./gradlew :apps:commerce-api:test --tests '*OrderConcurrencyTest' --rerun-tasks
```

| 시나리오 | 기대 결과 | 10회 실행 |
| --- | --- | --- |
| 재고 5개에 주문 8건 | 성공 5·재고 부족 3·최종 재고 0 | 10회 실패 |
| 잔액 10,000원에 4,000원 주문 3건 | 성공 2·잔액 부족 1·최종 잔액 2,000원 | 10회 실패 |
| 같은 주문 동시 확정 2건 | 성공 1·재확정 거절 1·재고와 포인트 차감 각 1회 | 10회 실패 |
| 2,000원 충전과 7,000원 결제 | 두 요청 성공·최종 잔액 5,000원 | 10회 실패 |

당시 총 40건 중 통과 0건이었다. 각 시나리오에서 기대한 결과와 불일치하는 항목이 있었다. 이후 통과 결과와 함께 보되, 한 번의 통과가 모든 실행 순서의 안전성을 증명하는 것은 아니다.
