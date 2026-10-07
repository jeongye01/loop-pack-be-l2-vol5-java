# SERIALIZABLE + 1회 재시도 실험 결과

`SERIALIZABLE`과 데드락 1회 재시도·0ms 대기를 적용한 뒤 S1을 01과 같은 부하 조건으로 실행했다.

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 데드락 요청은 새 트랜잭션에서 최대 1회, 0ms 대기로 재시도했다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 28~30 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1260~1260 | 0~0 | 없음 | 0~0 | 16~17 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4174~4200 | 0~26 | deadlock:0~26 | 0~26 | 10~11 | 0 | 통과 |

- **판정**: 10·30 RPS에서는 세 Run 모두 기술 오류가 없었고, 100 RPS에서는 재시도 후에도 0·1·26건의 기술 오류가 남았다. 요청 유실은 없었고 모든 DB 정합성 검증을 통과했다.
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
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1260 | 0 | 0 | 30.01 | 9.00 | 21.01 | 0.00 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4200 | 0 | 0 | 100.01 | 30.00 | 70.01 | 0.00 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4199 | 1 | 0 | 100.01 | 30.00 | 69.99 | 0.02 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4174 | 26 | 0 | 100.01 | 30.00 | 69.57 | 0.43 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 20.00 | 30.00 | 34.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 19.00 | 29.00 | 33.01 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 20.00 | 28.05 | 31.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 17.00 | 21.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 12.00 | 16.00 | 20.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 12.00 | 17.00 | 21.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 6.00 | 11.00 | 67.01 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 7.00 | 10.00 | 27.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 11.00 | 41.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 25.00 | 34.00 | 36.21 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 24.00 | 32.05 | 35.21 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 25.00 | 30.00 | 32.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 13.00 | 17.00 | 23.05 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 14.00 | 17.00 | 24.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 13.00 | 17.00 | 22.83 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 6.00 | 8.00 | 13.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 7.00 | 10.00 | 18.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 7.00 | 12.00 | 30.01 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 19.00 | 23.00 | 26.00 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 19.00 | 24.00 | 27.81 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 19.00 | 22.00 | 24.00 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 13.00 | 17.00 | 21.00 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1260 | 12.00 | 15.00 | 18.00 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1260 | 12.00 | 17.00 | 21.00 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4200 | 6.00 | 13.00 | 93.09 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4199 | 7.00 | 10.00 | 164.02 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4174 | 7.00 | 10.00 | 20.00 |

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
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1 | 14.00 | 14.00 | 14.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 26 | 121.00 | 163.00 | 179.75 |

## 해석

10·30 RPS에서는 세 Run 모두 기술 오류가 없었다. 100 RPS에서는 기술 오류가 0·1·26건 남아 1회 재시도만으로 고부하 교착을 모두 해소하지 못했다. 성공·품절 집계와 최종 DB 정합성은 모든 본 측정에서 통과했다. 기술 오류는 재시도 후에도 잠금을 얻지 못한 요청이다.

01 기준선과 비교하면 낮은 부하에서는 결과가 같고, 100 RPS에서는 데드락 기술 오류가 16~42건에서 0~26건으로 줄었다. 재시도는 일부 요청을 성공 또는 품절 거절로 재판정했지만, 동시 경합이 집중된 경우에는 한 번의 재시도 뒤에도 교착이 남았다.

## 측정 조건·한계

01과 같은 로컬 시스템 스펙과 S1 데이터 생성 규칙을 사용한다. 재시도 횟수와 대기 시간 외의 애플리케이션 설정은 변경하지 않는다.
