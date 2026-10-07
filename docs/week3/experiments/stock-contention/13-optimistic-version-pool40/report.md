# 낙관적 버전 락 자원 제한 실험 결과

## 상태

clean build와 동일한 자원 조건에서 재시도 0회로 실행했다.

## 고정 조건

| 항목 | 값 |
| --- | --- |
| 잠금 전략 | 낙관적 버전 락 |
| DB 격리 수준 | MySQL 기본값(REPEATABLE-READ) |
| 재시도 | 0회 |
| Spring Hikari | 최대 40, 유휴 30, 대기 3초 |
| MySQL | `max_connections=50` |

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 기본 격리수준에서 JPA @Version 낙관적 락을 켜고 재시도 없이 충돌과 기술 오류를 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 36~46 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1256~1259 | 1~4 | optimisticConflict:1~4 | 0~0 | 21~22 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4124~4155 | 45~76 | optimisticConflict:45~76 | 0~0 | 11~13 | 0 | 통과 |

- **판정**: 데드락은 발생하지 않았고 낙관적 충돌 기술 오류는 10 RPS 0건, 30 RPS 1~4건, 100 RPS 45~76건이었다. 모든 측정 Run의 DB 정합성 검증을 통과했다.
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
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1256 | 4 | 0 | 30.01 | 9.00 | 20.94 | 0.07 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1259 | 1 | 0 | 30.01 | 9.00 | 20.99 | 0.02 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1258 | 2 | 0 | 30.01 | 9.00 | 20.97 | 0.03 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4124 | 76 | 0 | 100.01 | 30.00 | 68.74 | 1.27 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4149 | 51 | 0 | 100.01 | 30.00 | 69.15 | 0.85 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4155 | 45 | 0 | 99.99 | 30.00 | 69.24 | 0.75 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 28.00 | 46.00 | 53.04 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 22.00 | 41.00 | 80.04 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 21.00 | 36.00 | 54.04 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 21.00 | 36.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 15.00 | 22.00 | 29.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 15.00 | 22.00 | 31.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 13.00 | 32.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 6.00 | 13.00 | 42.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 11.00 | 25.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 38.00 | 51.05 | 61.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 30.50 | 44.00 | 47.00 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 29.00 | 42.00 | 59.89 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 12.00 | 18.00 | 29.44 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 14.00 | 20.00 | 28.61 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 14.00 | 21.00 | 29.61 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 6.00 | 12.00 | 22.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 11.00 | 20.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 6.00 | 9.00 | 17.01 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 26.00 | 37.00 | 45.81 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 20.00 | 32.05 | 89.67 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 20.00 | 28.00 | 44.86 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1256 | 13.00 | 23.00 | 34.35 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1259 | 15.00 | 22.00 | 28.42 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1258 | 15.00 | 22.00 | 31.43 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4124 | 7.00 | 11.00 | 26.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4149 | 6.00 | 14.00 | 54.52 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4155 | 7.00 | 11.00 | 21.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 4 | 85.50 | 134.80 | 138.16 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1 | 22.00 | 22.00 | 22.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 2 | 19.00 | 26.20 | 26.84 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 76 | 18.00 | 96.75 | 129.25 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 51 | 13.00 | 73.00 | 98.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 45 | 38.00 | 185.80 | 203.28 |

## 해석

재시도하지 않는 낙관적 락은 경합이 낮은 10 RPS에서는 충돌이 없었지만, 100 RPS에서는 Run당 45~76건의 충돌을 빠르게 실패시켰다. 비관적 락처럼 기다리지 않아 전체 p95는 낮게 유지됐지만, 업무 흐름의 동시성 테스트는 40회 모두 기대 결과를 만족하지 못했다. 충돌을 사용자 업무 거절로 바꾸거나 제한된 재시도를 적용할지는 별도 정책으로 결정해야 한다.
