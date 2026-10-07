# 낙관적 버전 락 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 기본 격리수준에서 JPA @Version 낙관적 락을 켜고 재시도 없이 충돌과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 419~420 | 0~1 | optimisticConflict:0~1 | 0~0 | 36~46 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1255~1256 | 4~5 | optimisticConflict:4~5 | 0~0 | 18~23 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4109~4180 | 20~91 | optimisticConflict:20~91 | 0~0 | 8~13 | 0 | 통과 |

- **판정**: 데드락은 발생하지 않았고 기술 오류는 10 RPS 0~1건, 30 RPS 4~5건, 100 RPS 20~91건이었다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 테스트

| 테스트 | 통과 | 실패 | 건너뜀 |
| --- | ---: | ---: | ---: |
| [동시성] 한 고객의 잔액 10000원·충분한 재고에서 서로 다른 4000원 DRAFT 주문 3건을 동시에 확정하면 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2000원이다. | 0 | 10 | 0 |
| [동시성] 재고 5개에 주문 8건을 동시에 확정하면 성공 5·재고 부족 3·최종 재고 0이다. | 0 | 10 | 0 |
| [동시성] 같은 DRAFT 주문을 두 번 동시에 확정하면 한 건만 성공하고 재고·포인트는 한 번만 차감된다. | 0 | 10 | 0 |
| [동시성] 2000원 충전과 7000원 결제가 모두 성공하면 최종 잔액은 5000원이다. | 0 | 10 | 0 |
| verifiesActualConnectionIsolation() | 10 | 0 | 0 |

[테스트 원본 JSON](results/test-results.json)

## HTTP 본 측정 처리 결과

| Run | 전체 요청 수 | 성공 주문 | 품절 거절 | 기술 오류 | 전송되지 않은 요청 | 전체 TPS | 성공 TPS | 품절 거절 TPS | 기술 오류 TPS | 데이터 정합성 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 180 | 419 | 1 | 0 | 10.01 | 3.00 | 6.99 | 0.02 | passed |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 180 | 420 | 0 | 0 | 10.02 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1255 | 5 | 0 | 30.00 | 9.00 | 20.92 | 0.08 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1255 | 5 | 0 | 30.01 | 9.00 | 20.92 | 0.08 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1256 | 4 | 0 | 30.01 | 9.00 | 20.94 | 0.07 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4180 | 20 | 0 | 100.01 | 30.00 | 69.67 | 0.33 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4109 | 91 | 0 | 100.01 | 30.00 | 68.49 | 1.52 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4140 | 60 | 0 | 100.00 | 30.00 | 69.00 | 1.00 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 26.00 | 46.00 | 64.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 23.00 | 38.00 | 56.02 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 21.00 | 36.05 | 72.02 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 14.00 | 23.00 | 34.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 14.00 | 23.00 | 41.01 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 13.00 | 18.05 | 32.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 5.00 | 8.00 | 14.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 13.00 | 46.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 11.00 | 21.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 35.50 | 51.00 | 65.05 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 31.00 | 45.00 | 77.89 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 28.00 | 38.00 | 78.09 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 14.00 | 23.00 | 31.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 15.00 | 21.00 | 29.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 12.00 | 16.00 | 28.22 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 5.00 | 8.00 | 14.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 12.00 | 28.02 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 12.00 | 22.00 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 25.00 | 35.00 | 46.24 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 419 | 22.00 | 29.10 | 38.64 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 19.00 | 32.05 | 62.62 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1255 | 14.00 | 23.00 | 32.46 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1255 | 14.00 | 23.00 | 41.46 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1256 | 13.00 | 19.00 | 30.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4180 | 5.00 | 7.00 | 14.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4109 | 7.00 | 11.00 | 27.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4140 | 7.00 | 10.00 | 18.61 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 1 | 74.00 | 74.00 | 74.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 5 | 29.00 | 55.60 | 59.12 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 5 | 90.00 | 141.40 | 144.28 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 4 | 66.50 | 135.70 | 143.14 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 20 | 12.50 | 37.65 | 47.53 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 91 | 28.00 | 166.50 | 185.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 60 | 13.50 | 41.20 | 48.00 |

## 해석

동시성 테스트는 4개 업무 시나리오가 10회씩 모두 실패했고, 격리수준 확인은 10회 모두 통과했다. 일반 요청은 잠금 대기 없이 진행했지만 같은 `@Version`을 읽은 요청이 커밋 시 충돌해 기술 오류가 발생했다. S1 부하에서는 10 RPS의 기술 오류가 0~1건, 30 RPS가 4~5건, 100 RPS가 20~91건으로 증가했다. 데드락은 0건이었고 모든 Run의 재고·주문·잔액 DB 검증은 통과했다. 충돌 재시도를 추가하면 성공률은 개선될 수 있지만 재시도 증폭과 p95 변화를 별도 실험해야 한다.
