# 낙관적 버전 락 1회 재시도 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 기본 격리수준에서 JPA @Version 낙관적 락을 켜고 충돌을 새 트랜잭션에서 1회 재시도했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 24~26 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1256~1260 | 0~4 | optimisticConflict:0~4 | 0~0 | 16~17 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4183~4187 | 13~17 | optimisticConflict:13~17 | 0~0 | 7~24 | 0 | 통과 |

- **판정**: 실행 후 재시도 성공률·잔여 충돌·지연시간을 기록한다. 모든 측정 Run의 DB 정합성을 별도로 검증한다.
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
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1256 | 4 | 0 | 30.01 | 9.00 | 20.94 | 0.07 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1259 | 1 | 0 | 30.01 | 9.00 | 20.99 | 0.02 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4186 | 14 | 0 | 100.01 | 30.00 | 69.77 | 0.23 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4187 | 13 | 0 | 100.01 | 30.00 | 69.79 | 0.22 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4183 | 17 | 0 | 98.83 | 29.65 | 68.90 | 0.28 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 17.00 | 24.05 | 29.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 16.00 | 26.00 | 36.01 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 16.00 | 25.00 | 29.01 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 17.00 | 25.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 12.00 | 16.00 | 19.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 12.00 | 17.00 | 23.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 5.00 | 7.00 | 12.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 5.00 | 8.00 | 16.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 6.00 | 24.00 | 641.12 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 22.00 | 25.00 | 30.21 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 22.00 | 28.00 | 39.31 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 22.00 | 28.00 | 31.21 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 12.00 | 15.00 | 19.22 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 12.00 | 15.00 | 16.61 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 12.00 | 14.00 | 18.61 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 6.00 | 8.00 | 14.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 7.00 | 12.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 6.00 | 8.00 | 16.01 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 16.00 | 20.00 | 26.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 15.00 | 20.00 | 34.62 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 15.00 | 20.00 | 24.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1256 | 13.00 | 17.00 | 25.45 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1259 | 13.00 | 17.00 | 20.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 13.00 | 18.00 | 25.82 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4186 | 5.00 | 7.00 | 10.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4187 | 5.00 | 8.00 | 18.14 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4183 | 6.00 | 89.00 | 858.36 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 4 | 83.50 | 136.20 | 140.04 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1 | 18.00 | 18.00 | 18.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 14 | 10.00 | 25.75 | 28.35 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 13 | 10.00 | 14.20 | 15.64 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 17 | 16.00 | 39.40 | 47.08 |

## 해석

1회 재시도 적용 후 동시성 테스트는 4개 중 충전·결제와 동일 주문 확정이 10회 모두 통과했고, 잔액 경쟁과 재고 경쟁은 여전히 10회 모두 실패했다. S1 HTTP 부하에서는 10 RPS 충돌 0건, 30 RPS 0~4건, 100 RPS 13~17건으로 실험 10보다 충돌이 줄었다. 데드락은 발생하지 않았고 모든 측정 Run의 DB 정합성 검증을 통과했다. 재시도는 낮은 부하의 충돌을 성공으로 전환했지만, 높은 부하에서는 잔여 충돌과 p99 급등이 남아 재시도 횟수를 무제한으로 늘리는 방식은 적절하지 않다.
