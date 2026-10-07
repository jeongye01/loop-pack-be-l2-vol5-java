# 비관적 락 자원 제한 실험 결과

## 상태

낙관적 락 코드와 이전 혼합 결과를 제거한 뒤, clean build와 제한된 연결 조건으로 다시 실행했다.

## 고정 조건

| 항목 | 값 |
| --- | --- |
| 잠금 전략 | 비관적 쓰기 잠금 |
| DB 격리 수준 | MySQL 기본값(REPEATABLE-READ) |
| 재시도 | 0회 |
| Spring Hikari | 최대 40, 유휴 30, 대기 3초 |
| MySQL | `max_connections=50` |

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 비관적 쓰기 잠금과 커넥션 풀 40개에서 자원 포화와 잠금 대기를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 30~39 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1260~1260 | 0~0 | 없음 | 0~0 | 20~21 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4200~4200 | 0~0 | 없음 | 0~0 | 11~12 | 0 | 통과 |

- **판정**: 실행 후 잠금 대기·커넥션 획득 오류·지연시간을 기록한다. 모든 측정 Run의 DB 정합성을 별도로 검증한다.
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
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 99.99 | 30.00 | 69.99 | 0.00 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 100.01 | 30.00 | 70.00 | 0.00 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 22.00 | 39.05 | 51.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 19.00 | 30.05 | 36.02 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 21.00 | 36.00 | 106.28 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 14.00 | 21.00 | 53.03 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 13.00 | 21.00 | 33.01 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 13.00 | 20.00 | 37.03 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 11.00 | 22.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 6.00 | 12.00 | 48.02 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 6.00 | 12.00 | 102.11 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 29.00 | 49.05 | 53.21 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 25.00 | 35.00 | 39.63 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 28.00 | 42.00 | 214.95 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 15.00 | 20.00 | 22.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 14.00 | 23.00 | 34.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 14.00 | 18.05 | 31.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 7.00 | 13.00 | 28.02 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 10.00 | 37.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 6.00 | 13.00 | 61.05 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 22.00 | 27.00 | 32.81 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 18.00 | 23.00 | 27.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 20.00 | 29.00 | 48.81 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 14.00 | 23.00 | 87.51 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 13.00 | 20.00 | 33.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 12.00 | 21.00 | 41.41 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4200 | 7.00 | 11.00 | 19.01 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4200 | 6.00 | 13.00 | 57.02 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4200 | 6.00 | 12.00 | 167.04 |

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

## 해석

10·30·100 RPS 모두 기술 오류와 데드락 없이 성공·품절 거절로 처리됐다. 동시성 테스트 4개는 10회씩 모두 통과했고, 각 측정 Run의 DB 정합성도 통과했다.

## 검증

- 동시성 테스트 4개와 실제 커넥션 격리 수준 확인을 10회 실행한다.
- S1을 10·30·100 RPS, 각 60초·3회 실행한다.
- 성공, 품절 거절, 데드락, 잠금 대기 시간 초과, 커넥션 획득 오류를 분리해 기록한다.
- 각 Run 종료 후 주문·재고·잔액·결제 결과를 DB에서 검증한다.

실행 결과는 `results/` 아래 JSON에 기록하고, 실행 후 이 문서의 표를 갱신한다.
