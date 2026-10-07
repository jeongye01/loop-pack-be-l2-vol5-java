# SERIALIZABLE + 재시도 2회·커넥션 풀 200개 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 커넥션 풀 200개에서 재시도 2회·0ms의 DB 데드락과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 28~37 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1260~1260 | 0~0 | 없음 | 0~0 | 17~22 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4165~4200 | 0~35 | deadlock:0~35 | 0~35 | 8~16 | 0 | 통과 |

- **판정**: 커넥션 풀 획득 시간 초과는 발생하지 않았다. 10·30 RPS는 기술 오류 없이 완료됐고, 100 RPS에서는 데드락 기술 오류 0~35건이 남았다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 테스트

| 테스트 | 통과 | 실패 | 건너뜀 |
| --- | ---: | ---: | ---: |
| [동시성] 한 고객의 잔액 10000원·충분한 재고에서 서로 다른 4000원 DRAFT 주문 3건을 동시에 확정하면 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2000원이다. | 10 | 0 | 0 |
| [동시성] 재고 5개에 주문 8건을 동시에 확정하면 성공 5·재고 부족 3·최종 재고 0이다. | 0 | 10 | 0 |
| [동시성] 같은 DRAFT 주문을 두 번 동시에 확정하면 한 건만 성공하고 재고·포인트는 한 번만 차감된다. | 10 | 0 | 0 |
| [동시성] 2000원 충전과 7000원 결제가 모두 성공하면 최종 잔액은 5000원이다. | 10 | 0 | 0 |
| verifiesActualConnectionIsolation() | 10 | 0 | 0 |

[테스트 원본 JSON](results/test-results.json)

## HTTP 본 측정 처리 결과

| Run | 전체 요청 수 | 성공 주문 | 품절 거절 | 기술 오류 | 전송되지 않은 요청 | 전체 TPS | 성공 TPS | 품절 거절 TPS | 기술 오류 TPS | 데이터 정합성 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.00 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4165 | 35 | 0 | 100.01 | 30.00 | 69.42 | 0.58 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 100.01 | 30.00 | 70.00 | 0.00 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4197 | 3 | 0 | 100.01 | 30.00 | 69.95 | 0.05 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 24.00 | 37.05 | 47.03 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 21.00 | 31.00 | 37.01 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 19.00 | 28.00 | 32.03 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 17.00 | 21.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 14.00 | 22.00 | 81.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 15.00 | 20.00 | 24.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 16.00 | 61.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 6.00 | 8.00 | 15.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 10.00 | 20.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 29.00 | 42.05 | 47.63 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 26.00 | 35.05 | 40.21 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 25.00 | 31.00 | 36.26 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 14.00 | 17.00 | 22.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 14.00 | 18.00 | 24.83 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 15.00 | 20.00 | 26.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 8.00 | 16.00 | 50.02 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 9.00 | 17.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 9.00 | 24.00 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 23.00 | 31.05 | 40.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 19.00 | 25.00 | 30.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 18.00 | 23.00 | 30.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 13.00 | 18.00 | 21.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 14.00 | 24.00 | 87.64 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 15.00 | 20.00 | 24.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4165 | 7.00 | 14.00 | 37.36 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4200 | 6.00 | 8.00 | 14.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4197 | 7.00 | 10.00 | 19.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 0 | — | — | — |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 35 | 92.00 | 150.50 | 168.52 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 0 | — | — | — |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 3 | 61.00 | 64.60 | 64.92 |

## 동시성 실패 원인

- **재고 경쟁**: 서로 다른 주문이 같은 `product` 행을 SERIALIZABLE 격리 수준에서 함께 읽은 뒤 재고를 갱신했다. 공유 잠금을 배타 잠금으로 승격하는 과정에서 순환 대기가 발생했고, 재시도 2회 후에도 일부 요청이 데드락으로 종료됐다. 이 때문에 성공 5건·품절 거절 3건 대신 기술 오류가 발생했다.
- **포인트 경쟁·중복 확정·충전과 결제**: 각 시나리오는 기대 결과를 충족했다. 동일한 잠금 행에서 충돌하더라도 재시도 또는 상태 판정으로 기술 오류 없이 완료됐다.
- 커넥션 풀 획득 시간 초과는 발생하지 않았다. 확인된 기술 오류 원인은 MySQL 데드락이다.

## 해석

커넥션 풀 부족은 제거됐지만, 같은 재고 행을 공유 잠금으로 읽은 뒤 갱신하는 경로에서는 재시도 2회만으로 데드락을 모두 회복하지 못했다.
