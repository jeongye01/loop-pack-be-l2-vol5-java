# SERIALIZABLE + 재시도 2회·0ms 실험 결과

02 기준선과 같은 S1 부하에서 재시도 2회·0ms을 적용한다.

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 데드락 요청은 새 트랜잭션에서 최대 2회, 0ms 대기로 재시도했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 31~45 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1259~1260 | 0~1 | deadlock:0~1 | 0~1 | 19~21 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 2136~4186 | 14~780 | deadlock:3~67, unclassified:0~777 | 3~67 | 12~5368 | 1284 | 통과 |

- **판정**: 재시도 2회·0ms 설정에서도 10 RPS는 기술 오류 없이 완료됐고, 30·100 RPS에서는 일부 기술 오류가 남을 수 있다. 각 Run의 실제 오류 수와 미시작 요청은 표에서 확인하며, 완료된 Run의 DB 정합성을 함께 판정한다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 테스트

| 테스트 | 통과 | 실패 | 건너뜀 |
| --- | ---: | ---: | ---: |
| [동시성] 한 고객의 잔액 10000원·충분한 재고에서 서로 다른 4000원 DRAFT 주문 3건을 동시에 확정하면 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2000원이다. | 10 | 0 | 0 |
| [동시성] 재고 5개에 주문 8건을 동시에 확정하면 성공 5·재고 부족 3·최종 재고 0이다. | 2 | 8 | 0 |
| [동시성] 같은 DRAFT 주문을 두 번 동시에 확정하면 한 건만 성공하고 재고·포인트는 한 번만 차감된다. | 10 | 0 | 0 |
| [동시성] 2000원 충전과 7000원 결제가 모두 성공하면 최종 잔액은 5000원이다. | 10 | 0 | 0 |
| verifiesActualConnectionIsolation() | 10 | 0 | 0 |

[테스트 원본 JSON](results/test-results.json)

## HTTP 본 측정 처리 결과

| Run | 전체 요청 수 | 성공 주문 | 품절 거절 | 기술 오류 | 전송되지 않은 요청 | 전체 TPS | 성공 TPS | 품절 거절 TPS | 기술 오류 TPS | 데이터 정합성 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 180 | 420 | 0 | 0 | 10.38 | 3.11 | 7.27 | 0.00 | passed |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 180 | 420 | 0 | 0 | 10.01 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 180 | 420 | 0 | 0 | 10.02 | 3.00 | 7.01 | 0.00 | passed |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1259 | 1 | 0 | 30.01 | 9.00 | 20.99 | 0.02 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 2136 | 780 | 1284 | 73.21 | 27.94 | 33.16 | 12.11 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4186 | 14 | 0 | 100.01 | 30.00 | 69.77 | 0.23 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4133 | 67 | 0 | 100.01 | 30.00 | 68.89 | 1.12 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 29.00 | 45.00 | 54.01 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 22.50 | 35.00 | 38.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 21.00 | 31.00 | 35.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 15.00 | 21.00 | 29.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 14.50 | 20.00 | 24.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 14.00 | 19.00 | 23.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4716 | 8.00 | 5368.00 | 7870.20 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 12.00 | 28.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 22.00 | 92.01 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 34.00 | 51.00 | 62.84 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 31.00 | 36.00 | 39.42 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 28.00 | 33.00 | 35.21 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 15.00 | 20.00 | 30.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 15.00 | 19.00 | 27.22 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 15.00 | 18.00 | 22.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 7.00 | 10.00 | 19.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 7.00 | 11.00 | 25.02 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 27.05 | 89.00 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 27.00 | 38.00 | 43.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 22.00 | 26.00 | 28.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 21.00 | 25.00 | 29.81 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1259 | 15.00 | 21.10 | 26.42 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 14.00 | 20.00 | 23.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 14.00 | 19.00 | 23.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 2136 | 8.00 | 5784.75 | 8039.35 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4186 | 7.00 | 12.00 | 24.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4133 | 7.00 | 16.00 | 50.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1 | 54.00 | 54.00 | 54.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 780 | 3008.00 | 5907.35 | 8643.84 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 14 | 90.50 | 164.50 | 180.10 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 67 | 84.00 | 165.50 | 191.74 |

## 해석

- 재시도 2회·0ms는 데드락 원인을 제거하지 못했다. 동시성 테스트의 재고 경쟁은 10회 중 8회에서 기술 오류가 남았다.
- 100 RPS 첫 Run의 전체 p95가 5,368ms까지 늘었고 1,284건이 시작되지 않았다. 재시도와 잠금 경합이 꼬리 지연과 생성기 포화로 이어진 사례다.
- 기술 오류 780건은 데드락 3건과 커넥션 풀 획득 시간 초과 777건으로 나뉜다. 풀은 최대 40개가 모두 사용 중이었고 대기 요청이 3,000ms 제한을 넘었다.
- 따라서 재시도 횟수 증가는 정합성 해결책이 아니라 최종 실패를 줄이기 위한 완화책으로 평가한다. 다음 실험은 잠금 범위와 순서를 바꾼다.

## 측정 조건·한계

02와 같은 시스템 스펙·데이터·RPS를 사용한다. 재시도 횟수만 1회에서 2회로 바꾸고 대기 시간은 0ms로 유지했다.
