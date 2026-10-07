# SERIALIZABLE + 재시도 1회·10ms·커넥션 풀 200개 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 커넥션 풀 200개에서 재시도 1회·10ms의 DB 데드락과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 31~39 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1258~1260 | 0~2 | deadlock:0~2 | 0~2 | 21~34 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4095~4197 | 3~105 | deadlock:3~105 | 3~105 | 11~23 | 0 | 통과 |

- **판정**: 커넥션 풀 획득 시간 초과는 발생하지 않았다. 10 RPS는 기술 오류 없이 완료됐고, 30 RPS에서는 데드락 기술 오류가 0~2건, 100 RPS에서는 3~105건 남았다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
- **주의**: 성공률 약 30%는 A 재고를 계획 요청의 30%로 설정한 실험 조건의 결과다. 서버 처리 한계로 해석하지 않는다.

## 동시성 실패 원인

- **포인트 경쟁**: 세 주문이 같은 `users` 행을 SERIALIZABLE 격리 수준에서 함께 읽은 뒤 잔액을 갱신했다. 공유 잠금을 배타 잠금으로 승격하는 과정에서 순환 대기가 발생했고, 10ms 대기 후 한 번 더 실행해도 한 요청이 다시 데드락으로 종료됐다.
- **재고 경쟁**: 서로 다른 주문이 같은 `product` 행을 함께 읽은 뒤 재고를 갱신했다. 동일한 공유 잠금에서 배타 잠금으로 승격하려는 요청이 겹쳐 순환 대기가 발생했고, 재시도 후에도 일부 요청이 데드락으로 종료됐다.
- 두 실패 모두 커넥션 풀 부족이 아니라 MySQL의 데드락 감지에 따른 트랜잭션 롤백이다. 재시도는 잠금 순서를 바꾸지 않으므로 성공을 보장하지 않는다.

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
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1258 | 2 | 0 | 30.01 | 9.00 | 20.97 | 0.03 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4197 | 3 | 0 | 100.01 | 30.00 | 69.95 | 0.05 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4191 | 9 | 0 | 100.01 | 30.00 | 69.85 | 0.15 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4095 | 105 | 0 | 100.00 | 30.00 | 68.25 | 1.75 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 23.00 | 39.00 | 57.07 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 20.00 | 31.00 | 53.02 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 23.00 | 37.00 | 82.12 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 15.00 | 23.00 | 50.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 15.00 | 21.00 | 28.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 14.00 | 34.00 | 402.04 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 12.00 | 33.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 11.00 | 30.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 23.00 | 154.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 34.00 | 43.05 | 64.42 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 26.00 | 36.05 | 50.42 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 25.00 | 38.15 | 51.21 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 15.00 | 25.00 | 81.27 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 14.00 | 19.00 | 30.61 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 14.00 | 23.05 | 42.66 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 8.00 | 13.00 | 29.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 7.00 | 13.00 | 31.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 25.00 | 110.01 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 22.00 | 31.00 | 43.24 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 19.00 | 27.00 | 63.91 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 22.00 | 34.00 | 116.02 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1258 | 15.00 | 23.00 | 33.43 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 15.00 | 22.00 | 27.41 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 14.00 | 43.05 | 589.41 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4197 | 7.00 | 12.00 | 34.04 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4191 | 7.00 | 10.00 | 24.10 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4095 | 7.00 | 13.00 | 68.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 2 | 153.50 | 211.55 | 216.71 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 0 | — | — | — |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 0 | — | — | — |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 3 | 38.00 | 47.90 | 48.78 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 9 | 62.00 | 79.20 | 83.04 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 105 | 132.00 | 254.60 | 268.88 |

## 해석

커넥션 풀 획득 시간 초과는 없었고, 확인된 기술 오류는 모두 데드락이었다. 재시도 1회와 10ms 대기는 10 RPS의 오류를 제거했지만, 부하가 올라가면 같은 행의 공유 잠금 승격 충돌이 반복되어 30·100 RPS에서 데드락이 남았다. 동시성 테스트에서도 포인트 행과 재고 행의 잠금 승격 데드락이 재현됐다.
