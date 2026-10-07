# SERIALIZABLE + 커넥션 풀 200개 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 커넥션 풀을 충분히 확보한 뒤 DB 데드락과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 29~40 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1260~1260 | 0~0 | 없음 | 0~0 | 20~21 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4146~4191 | 9~54 | deadlock:9~54 | 9~54 | 10~13 | 0 | 통과 |

- **판정**: 커넥션 풀 획득 시간 초과는 발생하지 않았다. 10·30 RPS는 기술 오류 없이 완료됐고, 100 RPS에서는 데드락 기술 오류 9~54건이 남았다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
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
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4191 | 9 | 0 | 100.01 | 30.00 | 69.86 | 0.15 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4174 | 26 | 0 | 100.01 | 30.00 | 69.57 | 0.43 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4146 | 54 | 0 | 100.01 | 30.00 | 69.11 | 0.90 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 22.00 | 40.00 | 94.14 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 21.00 | 29.00 | 39.01 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 21.00 | 31.05 | 40.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 15.00 | 21.00 | 28.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 15.00 | 20.00 | 26.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 15.00 | 21.00 | 28.02 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 10.00 | 18.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 11.00 | 25.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 13.00 | 34.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 30.00 | 41.05 | 46.84 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 24.00 | 29.00 | 35.84 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 27.00 | 37.00 | 42.26 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 15.00 | 20.00 | 23.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 15.00 | 21.00 | 31.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 16.00 | 20.00 | 27.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 7.00 | 9.05 | 17.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 7.00 | 11.00 | 23.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 13.00 | 31.01 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 21.00 | 38.00 | 115.29 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 20.00 | 29.00 | 40.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 19.00 | 26.00 | 31.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 15.00 | 21.00 | 29.41 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 14.00 | 20.00 | 24.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 15.00 | 21.00 | 28.82 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4191 | 7.00 | 10.00 | 20.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4174 | 7.00 | 11.00 | 22.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4146 | 7.00 | 12.00 | 33.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 0 | — | — | — |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 9 | 9.00 | 16.20 | 18.44 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 26 | 27.50 | 86.50 | 99.25 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 54 | 19.00 | 77.35 | 104.23 |

## 해석

실행 후 커넥션 풀 고갈과 DB 잠금 오류를 분리해 기록한다.

실행 후 동시성 테스트 10회는 4개 시나리오 모두 데드락으로 실패했다. S1 부하 10·30·100 RPS 각 3회에서는 커넥션 풀 오류 없이 100 RPS에서만 데드락이 발생했다.
