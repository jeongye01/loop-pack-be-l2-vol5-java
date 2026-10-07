# 비관적 쓰기 잠금 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 커넥션 풀을 충분히 확보한 뒤 DB 데드락과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 26~47 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1260~1260 | 0~0 | 없음 | 0~0 | 15~16 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4200~4200 | 0~0 | 없음 | 0~0 | 12~19 | 0 | 통과 |

- **판정**: 커넥션 풀 획득 시간 초과와 데드락은 발생하지 않았다. 모든 RPS 단계에서 성공 주문과 품절 거절만 집계됐고, 모든 측정 Run의 DB 정합성 검증을 통과했다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 테스트

| 테스트 | 통과 | 실패 | 건너뜀 |
| --- | ---: | ---: | ---: |
| [동시성] 한 고객의 잔액 10000원·충분한 재고에서 서로 다른 4000원 DRAFT 주문 3건을 동시에 확정하면 성공 2·잔액 부족 1·기술 오류 0·최종 잔액 2000원이다. | 10 | 0 | 0 |
| [동시성] 재고 5개에 주문 8건을 동시에 확정하면 성공 5·재고 부족 3·최종 재고 0이다. | 10 | 0 | 0 |
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
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 100.01 | 30.00 | 70.01 | 0.00 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 100.01 | 30.00 | 70.00 | 0.00 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 100.00 | 30.00 | 70.00 | 0.00 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 23.00 | 47.00 | 64.04 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 17.00 | 27.05 | 42.07 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 17.00 | 26.00 | 32.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 11.00 | 16.00 | 27.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 10.00 | 15.00 | 25.02 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 10.00 | 15.00 | 24.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 6.00 | 12.00 | 27.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 19.00 | 127.04 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 6.00 | 16.00 | 87.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 40.00 | 56.00 | 90.32 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 20.00 | 34.00 | 49.42 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 23.00 | 29.00 | 32.21 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 11.00 | 16.00 | 25.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 10.00 | 15.00 | 25.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 10.00 | 19.00 | 62.15 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 7.00 | 15.00 | 28.01 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 8.00 | 22.05 | 83.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 39.00 | 152.04 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 21.00 | 35.00 | 56.62 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 16.00 | 24.00 | 37.81 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 16.00 | 23.00 | 28.43 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 11.00 | 16.00 | 27.41 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 10.00 | 15.00 | 28.51 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 9.00 | 14.00 | 20.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4200 | 6.00 | 10.00 | 24.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4200 | 6.00 | 17.00 | 157.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4200 | 6.00 | 10.00 | 31.02 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 0 | — | — | — |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 0 | — | — | — |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 0 | — | — | — |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 0 | — | — | — |

## 동시성 제어 결과

- `Order`, `User`, `Product`를 `PESSIMISTIC_WRITE`로 먼저 잠그고 변경했다.
- 동일 행을 경쟁한 요청은 데드락으로 롤백되지 않고 앞선 트랜잭션 종료 후 최신 상태를 읽었다.
- 재고 경쟁은 성공 5건·품절 거절 3건, 포인트 경쟁은 성공 2건·잔액 부족 1건으로 10회 모두 기대 결과를 충족했다.
- 확인된 기술 오류와 커넥션 풀 대기 시간 초과는 0건이었다.

## 해석

DB 기본 격리 수준(REPEATABLE-READ)에서 비관적 쓰기 잠금을 사용하자 SERIALIZABLE 실험에서 발생한 공유 잠금 승격 데드락이 재현되지 않았다. 같은 부하에서 모든 요청이 업무 성공 또는 업무 거절로 분류됐고, 기술 오류 없이 DB 정합성도 유지됐다.

## 이전 실험과 비교

| 비교 대상 | 동시성 테스트 | 100 RPS 기술 오류 | 100 RPS p95 범위 |
| --- | --- | ---: | ---: |
| SERIALIZABLE + 재시도 1회·0ms·풀 200개 (08) | 2개 통과 / 2개 실패 | 6~41건 | 11~12ms |
| 기본 격리 수준 + 비관적 쓰기 잠금 (09) | 4개 통과 / 0개 실패 | 0건 | 12~19ms |

비관적 잠금은 대기 후 최신 상태를 판정하므로 품절·잔액 부족은 업무 거절로 남고, 데드락 기술 오류는 발생하지 않았다. p95는 SERIALIZABLE 재시도 실험보다 소폭 높을 수 있지만, 기술 오류와 재시도 증폭 없이 기대한 업무 결과를 유지했다.
