# 낙관적 버전 락 재시도 2회 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 기본 격리수준에서 JPA `@Version` 낙관적 락과 최대 2회·0ms 재시도를 적용했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 32~36 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1257~1260 | 0~3 | optimisticConflict:0~3 | 0~0 | 21~25 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4082~4164 | 36~118 | optimisticConflict:36~118 | 0~0 | 11~25 | 0 | 통과 |

- **판정**: 데드락과 전송되지 않은 요청은 없었다. 기술 오류는 10 RPS에서 0건, 30 RPS에서 0~3건, 100 RPS에서 36~118건으로 모두 낙관적 버전 충돌이었다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
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
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 180 | 420 | 0 | 0 | 10.02 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1257 | 3 | 0 | 30.01 | 9.00 | 20.96 | 0.05 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.00 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4147 | 53 | 0 | 100.02 | 30.01 | 69.13 | 0.88 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4164 | 36 | 0 | 100.01 | 30.00 | 69.40 | 0.60 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4082 | 118 | 0 | 100.01 | 30.00 | 68.04 | 1.97 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 22.00 | 36.00 | 41.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 21.00 | 32.00 | 37.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 24.00 | 36.05 | 47.01 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 25.00 | 81.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 15.00 | 21.00 | 33.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 14.00 | 22.00 | 50.01 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 13.00 | 47.01 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 11.00 | 37.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 25.00 | 122.02 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 27.00 | 39.00 | 43.21 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 27.00 | 35.05 | 42.21 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 30.00 | 38.05 | 48.21 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 13.00 | 27.00 | 77.71 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 15.00 | 22.00 | 33.22 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 14.00 | 22.00 | 50.61 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 7.00 | 14.00 | 38.02 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 7.00 | 12.00 | 26.04 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 8.00 | 24.00 | 67.03 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 20.00 | 31.00 | 38.81 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 20.00 | 26.00 | 29.81 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 23.00 | 33.00 | 45.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1257 | 13.00 | 25.00 | 65.52 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 15.00 | 21.00 | 33.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 14.00 | 22.00 | 47.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4147 | 7.00 | 11.00 | 21.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4164 | 7.00 | 10.00 | 21.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4082 | 7.00 | 16.00 | 49.19 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 3 | 131.00 | 148.10 | 149.62 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 53 | 126.00 | 217.40 | 246.52 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 36 | 128.50 | 268.25 | 294.55 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 118 | 83.00 | 278.75 | 335.69 |

## 해석

재시도 2회는 10 RPS에서 충돌 없이 완료했고 30 RPS에서는 대부분의 충돌을 업무 결과로 전환했다. 100 RPS에서는 충돌이 36~118건 남았지만 Hikari 획득 시간 초과나 데드락은 발생하지 않았다. 전체 p95는 11~25ms였고 기술 오류 p95는 217~279ms 범위였다. 재시도가 커넥션 풀을 고갈시키지는 않았으나, 높은 경합에서는 추가 시도만으로 모든 충돌을 해소하지 못했다.
