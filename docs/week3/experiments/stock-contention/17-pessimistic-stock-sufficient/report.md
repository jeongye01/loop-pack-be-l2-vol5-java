# 비관적 쓰기 잠금 실험 결과

## 핵심 결과

S1은 서로 다른 사용자가 같은 상품 A를 구매하고, 모든 계획 주문을 처리하고도 재고가 남도록 초기화한 실험이다. 품절 영향을 제외하고 재고 행의 비관적 쓰기 잠금 경합과 처리량·응답 시간을 측정했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 600 | 0~0 | 0~0 | 없음 | 0~0 | 34~42 | 0 | 통과 |
| 30 | 3회 | 1800 | 1800 | 0~0 | 0~0 | 없음 | 0~0 | 19~19 | 0 | 통과 |
| 100 | 3회 | 6000 | 6000 | 0~0 | 0~0 | 없음 | 0~0 | 10~11 | 0 | 통과 |

- **판정**: 9개 측정 Run에서 계획한 25,200건이 모두 성공했다. 품절 거절·데드락·기술 오류·미시작 요청은 0건이었고, 모든 Run의 DB 정합성 검증을 통과했다.
- **응답 시간**: 전체 요청 p95는 10 RPS에서 34~42ms, 30 RPS에서 19ms, 100 RPS에서 10~11ms였다. 측정 환경에서는 목표 요청률을 모두 수용했다.


## HTTP 본 측정 처리 결과

| Run | 전체 요청 수 | 성공 주문 | 품절 거절 | 기술 오류 | 전송되지 않은 요청 | 전체 TPS | 성공 TPS | 품절 거절 TPS | 기술 오류 TPS | 데이터 정합성 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 600 | 0 | 0 | 0 | 10.01 | 10.01 | 0.00 | 0.00 | passed |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 600 | 0 | 0 | 0 | 10.01 | 10.01 | 0.00 | 0.00 | passed |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 600 | 0 | 0 | 0 | 10.01 | 10.01 | 0.00 | 0.00 | passed |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 1800 | 0 | 0 | 0 | 30.01 | 30.01 | 0.00 | 0.00 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 1800 | 0 | 0 | 0 | 30.01 | 30.01 | 0.00 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 1800 | 0 | 0 | 0 | 30.01 | 30.01 | 0.00 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 6000 | 0 | 0 | 0 | 100.01 | 100.01 | 0.00 | 0.00 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 6000 | 0 | 0 | 0 | 100.01 | 100.01 | 0.00 | 0.00 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 6000 | 0 | 0 | 0 | 100.01 | 100.01 | 0.00 | 0.00 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 31.00 | 42.00 | 52.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 29.00 | 38.00 | 47.03 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 28.00 | 34.00 | 44.06 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 14.00 | 19.00 | 27.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 14.00 | 19.00 | 27.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 14.00 | 19.00 | 30.01 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 10.00 | 18.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 11.00 | 36.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 10.00 | 20.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 31.00 | 42.00 | 52.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 29.00 | 38.00 | 47.03 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 28.00 | 34.00 | 44.06 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 14.00 | 19.00 | 27.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 14.00 | 19.00 | 27.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 14.00 | 19.00 | 30.01 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 10.00 | 18.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 11.00 | 36.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 10.00 | 20.00 |

## 품절 거절 응답 시간

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

재고가 충분한 조건에서는 품절 응답이 측정 결과에 섞이지 않았고, 비관적 재고 잠금으로도 L3 100 RPS까지 모든 요청이 성공했다. 이 결과는 고정된 로컬 자원과 짧은 트랜잭션에서의 측정값이며, 원자적 UPDATE 실험과 같은 조건에서 비교해야 한다.
