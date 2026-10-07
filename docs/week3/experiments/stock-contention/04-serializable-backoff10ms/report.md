# SERIALIZABLE + 재시도 1회·10ms 실험 결과

02 기준선과 같은 S1 부하에서 재시도 1회·10ms을 적용한다.

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 데드락 요청은 새 트랜잭션에서 최대 1회, 10ms 대기로 재시도했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 31~43 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1259~1260 | 0~1 | deadlock:0~1 | 0~1 | 19~22 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4192~4199 | 1~8 | deadlock:1~8 | 1~8 | 8~11 | 0 | 통과 |

- **판정**: 재시도 1회·10ms 설정에서도 10 RPS는 기술 오류 없이 완료됐고, 30·100 RPS에서는 일부 기술 오류가 남을 수 있다. 각 Run의 실제 오류 수와 미시작 요청은 표에서 확인하며, 완료된 Run의 DB 정합성을 함께 판정한다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 테스트

| 테스트 | 통과 | 실패 | 건너뜀 |
| --- | ---: | ---: | ---: |
| [동시성] 한 고객의 잔액 10000원·충분한 재고에서 서로 다른 4000원 DRAFT 주문 3건을 동시에 확정하면 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2000원이다. | 0 | 10 | 0 |
| [동시성] 재고 5개에 주문 8건을 동시에 확정하면 성공 5·재고 부족 3·최종 재고 0이다. | 1 | 9 | 0 |
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
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1259 | 1 | 0 | 30.01 | 9.00 | 20.99 | 0.02 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4198 | 2 | 0 | 100.01 | 30.00 | 69.98 | 0.03 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4199 | 1 | 0 | 100.01 | 30.00 | 69.99 | 0.02 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4192 | 8 | 0 | 100.01 | 30.00 | 69.87 | 0.13 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 27.00 | 43.05 | 51.03 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 22.00 | 38.00 | 68.05 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 22.00 | 31.05 | 38.01 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 15.00 | 22.00 | 44.02 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 14.00 | 20.00 | 27.01 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 15.00 | 19.00 | 24.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 6.00 | 8.00 | 18.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 6.00 | 8.00 | 16.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 6.00 | 11.00 | 29.01 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 37.00 | 48.00 | 60.68 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 30.00 | 40.00 | 69.05 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 27.50 | 33.05 | 38.63 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 16.00 | 23.00 | 57.54 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 15.00 | 20.00 | 27.44 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 14.00 | 18.00 | 20.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 6.00 | 9.00 | 22.01 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 9.00 | 16.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 6.00 | 8.00 | 14.02 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 26.00 | 34.00 | 40.81 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 20.00 | 31.00 | 59.62 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 21.00 | 26.00 | 36.43 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1259 | 14.00 | 22.00 | 30.42 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 14.00 | 20.00 | 26.41 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 15.00 | 20.00 | 24.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4198 | 6.00 | 8.00 | 15.03 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4199 | 6.00 | 8.00 | 15.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4192 | 7.00 | 11.00 | 33.18 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1 | 87.00 | 87.00 | 87.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 2 | 55.50 | 67.65 | 68.73 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1 | 36.00 | 36.00 | 36.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 8 | 57.50 | 92.65 | 98.53 |

## 해석

- 10ms 대기는 100 RPS의 기술 오류를 1~8건으로 줄였지만, 동시성 테스트 전체 통과를 만들지는 못했다.
- 대기 시간은 충돌을 없애지 않고 요청 완료 시점을 뒤로 미루는 조정값이다. 효과가 부하와 실행 순서에 따라 달라지므로 잠금 설계를 대신하지 않는다.

## 측정 조건·한계

02와 같은 시스템 스펙·데이터·RPS를 사용한다. 재시도 횟수는 1회로 유지하고 대기 시간만 0ms에서 10ms로 바꿨다.
