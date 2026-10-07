# SERIALIZABLE 실험 결과

## 핵심 결과

S1은 모든 주문이 같은 상품 A를 구매하고, A 재고를 계획 요청 수의 30%로 둔 실험이다. 목표 RPS의 요청은 모두 시작됐지만 부하가 올라갈수록 데드락 기술 오류가 늘었다.

| 목표 RPS | 측정 횟수 | Run당 전체 요청 | Run당 성공 주문 | Run당 품절 거절 | Run당 기술 오류 | 기술 오류 원인 | Run당 데드락 범위 | p95 범위(ms) | 전송되지 않은 요청 | 데이터 정합성 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 3회 | 600 | 180 | 420~420 | 0~0 | 없음 | 0~0 | 33~35 | 0 | 통과 |
| 30 | 3회 | 1800 | 540 | 1241~1260 | 0~19 | deadlock:0~19 | 0~19 | 19~23 | 0 | 통과 |
| 100 | 3회 | 6000 | 1800 | 4158~4184 | 16~42 | deadlock:16~42 | 16~42 | 9~11 | 0 | 통과 |

- **판정**: 10 RPS에서는 데드락이 없었고, 30 RPS부터 데드락이 관찰됐다. 요청 유실은 없었지만 기술 오류 0 조건은 만족하지 못했다.
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
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 540 | 1241 | 19 | 0 | 30.01 | 9.00 | 20.69 | 0.32 | passed |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 540 | 1250 | 10 | 0 | 30.01 | 9.00 | 20.84 | 0.17 | passed |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 1800 | 4158 | 42 | 0 | 100.01 | 30.00 | 69.31 | 0.70 | passed |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 1800 | 4184 | 16 | 0 | 100.01 | 30.00 | 69.74 | 0.27 | passed |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 1800 | 4184 | 16 | 0 | 100.01 | 30.00 | 69.74 | 0.27 | passed |

## 전체 요청 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 600 | 20.00 | 35.05 | 43.01 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 600 | 20.00 | 34.00 | 45.09 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 600 | 18.00 | 33.00 | 45.02 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1800 | 13.00 | 19.00 | 30.01 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1800 | 13.00 | 23.00 | 154.02 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1800 | 13.00 | 19.00 | 37.03 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 6000 | 7.00 | 11.00 | 120.04 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 6000 | 6.00 | 9.00 | 56.01 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 6000 | 7.00 | 9.00 | 15.00 |

## 성공 주문 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 180 | 27.00 | 39.05 | 69.26 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 180 | 26.00 | 37.05 | 48.78 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 180 | 24.00 | 34.00 | 41.05 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 540 | 13.00 | 18.00 | 28.61 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 540 | 14.00 | 23.00 | 51.22 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 540 | 13.00 | 22.00 | 38.27 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 1800 | 6.00 | 9.00 | 18.00 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 1800 | 6.00 | 8.00 | 15.00 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 1800 | 6.00 | 9.00 | 15.00 |

## 품절 거절 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 420 | 19.00 | 27.00 | 34.43 |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 420 | 19.00 | 27.05 | 44.24 |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 420 | 17.00 | 28.00 | 45.86 |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 1260 | 13.00 | 20.00 | 31.41 |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 1241 | 13.00 | 21.00 | 37.20 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 1250 | 12.00 | 17.00 | 25.51 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 4158 | 7.00 | 11.15 | 151.16 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 4184 | 6.00 | 10.00 | 81.17 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 4184 | 7.00 | 9.00 | 14.00 |

## 기술 오류 응답 시간

| Run | 요청 수 | p50(ms) | p95(ms) | p99(ms) |
| --- | ---: | ---: | ---: | ---: |
| [measure-S1-10rps-01](results/measure-S1-10rps-01/result.json) | 0 | — | — | — |
| [measure-S1-10rps-02](results/measure-S1-10rps-02/result.json) | 0 | — | — | — |
| [measure-S1-10rps-03](results/measure-S1-10rps-03/result.json) | 0 | — | — | — |
| [measure-S1-30rps-01](results/measure-S1-30rps-01/result.json) | 0 | — | — | — |
| [measure-S1-30rps-02](results/measure-S1-30rps-02/result.json) | 19 | 320.00 | 581.00 | 631.40 |
| [measure-S1-30rps-03](results/measure-S1-30rps-03/result.json) | 10 | 75.50 | 152.90 | 166.58 |
| [measure-S1-100rps-01](results/measure-S1-100rps-01/result.json) | 42 | 68.00 | 143.85 | 175.78 |
| [measure-S1-100rps-02](results/measure-S1-100rps-02/result.json) | 16 | 61.50 | 139.25 | 144.65 |
| [measure-S1-100rps-03](results/measure-S1-100rps-03/result.json) | 16 | 56.50 | 134.25 | 137.25 |

## 해석

### 부하와 데드락 발생 시점

- S1은 모든 주문이 상품 A를 구매하고 A 재고를 계획 요청의 30%로 둔 조건이다. 품절 거절은 실험에서 예상한 업무 결과다.
- 10 RPS에서는 3회 모두 데드락이 없었다. 30 RPS부터 데드락이 관찰됐고(0·19·10건), 100 RPS에서는 매회 발생했다(42·16·16건).
- 목표 RPS의 요청은 모두 전송됐고 최종 데이터 정합성은 모든 본 측정에서 통과했다. 다만 데드락 요청은 기술 오류로 끝나 성공·업무 거절 기대값을 충족하지 못했다.

### 원인 확인

`SHOW ENGINE INNODB STATUS`에서 각 교착의 잠금 그래프를 확인했다. 두 트랜잭션이 같은 행의 공유 잠금(S)을 보유한 상태에서 JPA flush의 UPDATE를 위해 배타 잠금(X) 승격을 동시에 요청했고, 서로의 공유 잠금이 풀리기를 기다리는 순환 대기가 생겼다. MySQL은 교착 희생 트랜잭션 하나를 롤백했다.

- 재고 경쟁: `product.id=1` UPDATE에서 두 트랜잭션이 S를 보유하고 X를 대기했다.
- 포인트 경쟁: `users.id=1` UPDATE에서 두 트랜잭션이 S를 보유하고 X를 대기했다.
- 같은 주문 확정: `orders.id=1` UPDATE에서 두 트랜잭션이 S를 보유하고 X를 대기했다.
- 충전·결제: `users.id=1` UPDATE에서 같은 S→X 교착이 발생했고 한쪽 요청이 롤백됐다.

### 다음 실험

다음 실험에서는 `SERIALIZABLE`을 유지하고 데드락으로 롤백된 요청을 **대기 0ms로 새 트랜잭션에서 1회 재시도**한다. 재시도 전 최신 재고·잔액·주문 상태를 다시 읽고, 재시도 후에도 실패하면 기술 오류로 기록한다. 성공·품절·기술 오류 TPS와 p50·p95·p99를 이번 실험과 같은 조건으로 비교한다.

## 측정 조건·한계

- Mac14,2·8코어·16 GiB, Docker CPU 8·메모리 약 7.65 GiB, MySQL 8.0.46. 서버 기본 격리는 REPEATABLE READ, 애플리케이션 세션은 SERIALIZABLE이다. 시작 시 스왑 약 10 GiB 사용 중인 로컬 환경이며 운영 처리량으로 일반화하지 않는다.
- 지연 시간은 밀리초 정밀도다. 품절 경계는 첫 품절 응답 관찰 시각이다. 데드락은 요청 ID·스레드·처리 시간으로 서버 로그와 연결해 분류했다.
- Checkstyle main/test와 ArchUnit은 통과했다. 기존 동시성 테스트의 기대값과 제품 코드는 변경하지 않았다.
