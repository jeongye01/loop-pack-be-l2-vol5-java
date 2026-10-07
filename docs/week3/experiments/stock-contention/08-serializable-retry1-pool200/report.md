# SERIALIZABLE + 재시도 1회·커넥션 풀 200개 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 커넥션 풀을 충분히 확보한 뒤 DB 데드락과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 36~40 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1250~1260 | 0~10 | deadlock:0~10 | 0~10 | 21~27 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4159~4194 | 6~41 | deadlock:6~41 | 6~41 | 11~12 | 0 | 통과 |

- **판정**: 커넥션 풀 획득 시간 초과는 발생하지 않았다. 10 RPS는 기술 오류 없이 완료됐고, 30 RPS에서는 데드락 기술 오류가 0~10건, 100 RPS에서는 6~41건 남았다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 테스트

| 테스트 | 통과 | 실패 | 건너뜀 |
| --- | ---: | ---: | ---: |
| [동시성] 한 고객의 잔액 10000원·충분한 재고에서 서로 다른 4000원 DRAFT 주문 3건을 동시에 확정하면 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2000원이다. | 0 | 10 | 0 |
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
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1250 | 10 | 0 | 30.01 | 9.00 | 20.84 | 0.17 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1251 | 9 | 0 | 30.01 | 9.00 | 20.86 | 0.15 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4194 | 6 | 0 | 100.01 | 30.00 | 69.90 | 0.10 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4159 | 41 | 0 | 100.00 | 30.00 | 69.32 | 0.68 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4191 | 9 | 0 | 100.01 | 30.00 | 69.86 | 0.15 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 25.50 | 40.00 | 48.02 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 23.00 | 36.00 | 43.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 23.00 | 39.05 | 87.03 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 21.00 | 31.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 15.00 | 25.00 | 99.13 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 15.00 | 27.00 | 96.10 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 11.00 | 23.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 11.00 | 52.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 12.00 | 35.01 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 31.50 | 44.00 | 48.42 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 28.00 | 40.00 | 43.21 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 30.00 | 39.05 | 44.42 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 14.00 | 18.00 | 27.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 15.00 | 28.05 | 65.22 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 14.00 | 30.05 | 102.10 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 7.00 | 10.05 | 19.01 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 8.00 | 13.00 | 24.02 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 11.00 | 25.00 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 23.00 | 36.00 | 47.24 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 22.00 | 30.05 | 35.81 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 20.00 | 39.05 | 98.91 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 13.00 | 21.00 | 32.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1250 | 15.00 | 23.00 | 37.51 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1251 | 15.00 | 25.00 | 44.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4194 | 7.00 | 10.00 | 26.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4159 | 7.00 | 10.00 | 20.42 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4191 | 7.00 | 12.00 | 40.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 0 | — | — | — |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 10 | 272.00 | 424.90 | 438.58 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 9 | 157.00 | 285.20 | 285.84 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6 | 50.50 | 62.00 | 62.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 41 | 198.00 | 369.00 | 388.20 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 9 | 32.00 | 54.00 | 58.80 |

## 동시성 실패 원인

- **포인트 경쟁**: 같은 `users` 행을 SERIALIZABLE 격리 수준에서 함께 읽은 뒤 잔액을 갱신하는 과정에서 공유 잠금의 배타 잠금 승격 데드락이 발생했다. 재시도 1회 후에도 두 경쟁 테스트에서 기술 오류가 남았다.
- **재고 경쟁**: 서로 다른 주문이 같은 `product` 행을 읽고 재고를 갱신하면서 동일한 공유 잠금 승격 데드락이 발생했다. 기대한 품절 거절 대신 일부 요청이 기술 오류로 종료됐다.
- 확인된 기술 오류 원인은 모두 MySQL 데드락이며, 커넥션 풀 획득 시간 초과는 없었다.

## 해석

풀을 200개로 확보해 커넥션 부족은 제거했지만, 재시도 1회·0ms만으로 같은 행의 잠금 승격 데드락을 모두 회복하지 못했다. 10 RPS에서는 오류가 없었고, 부하가 증가할수록 데드락 빈도가 증가했다.
