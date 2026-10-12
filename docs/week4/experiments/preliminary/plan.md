# 상품 목록 조회 성능 실험 계획

> 이 문서는 초기 검토 자료다. 현재 실행 절차는 [week4 공통 프로토콜](../protocol.md), 첫 실험은 [baseline 계획](../01-baseline/plan.md), 실제 장비는 [EC2 사양](../environments/ec2.md)을 따른다. 아래의 기존 부하 후보를 확정값으로 사용하지 않으며, 공통 부하 레벨은 baseline 완료 후 선정한다.

- 작성일: 2026-10-13
- 상태: 검토 초안. 아래 부하·수집 설정은 제안이며 아직 적용하지 않았다.
- 목적: 같은 상품 목록을 반환하면서 SQL·인덱스 개선과 캐시가 줄이는 비용을 확인한다.
- 근거: [week4 과제](../../../../.handoff/week4-%EA%B3%BC%EC%A0%9C.md), [예비 측정](report.md), [week3 프로토콜](../../../week3/experiments/protocol.md).

## 1. 측정할 요청과 비교 순서

```http
GET /api/v1/products?brandId=7&sort=price_asc&page=0&size=20
X-USER-ID: 1
```

| 항목 | 고정할 내용 |
| --- | --- |
| 데이터 | `uniform-10k-v1`: 브랜드 100개·상품 10,000개·좋아요 999,493개. CSV 체크섬·DB 적재 검증 결과 연결 |
| 조회 | 브랜드 7, 가격 오름차순, 첫 페이지, 크기 20. 기존 동점 정렬·삭제 조건 유지 |
| 결과 | 정렬된 상품 ID·가격·좋아요 수·나머지 응답 필드·페이지 정보를 기준 응답과 비교 |
| 비교 순서 | A 기존 DB 조회 → B SQL/인덱스 개선 → C 선택한 조회의 cache miss/hit |
| 변경 단위 | 인덱스와 쿼리를 각각 적용했다면 별도 버전으로 기록. 동시에 바꾼 경우 개별 효과로 해석하지 않음 |

### 같은 결과인지 확인하기

**응답 불일치**는 같은 데이터로 같은 요청을 보냈는데, 개선 전후에 실제 응답 내용이 달라지는 것이다. 예를 들어 브랜드 7의 가격순 첫 페이지에서 상품 순서가 바뀌거나, 좋아요 수·전체 건수가 틀리거나, 삭제된 상품이 나타나는 경우다. 빨라져도 결과가 틀리면 올바른 개선으로 보지 않는다.

JSON 필드가 적힌 순서는 달라도 괜찮다. 실제 필드 값과 목록 안의 상품 순서를 비교하고, 다르면 어느 상품의 어떤 값이 달라졌는지 남긴다. 타임아웃으로 응답을 못 받은 경우는 내용 불일치가 아니라 **요청 실패**로 따로 기록한다. 이 비교는 데이터 변경이 없는 성능 실험 기준이며, 관리자 변경 실험에서는 변경 후 기대 결과와 비교한다.

현재 `ProductUseCase.findCustomerProductPage`는 목록을 가져온 뒤 상품별 재고·좋아요 수를 조회하고 브랜드를 조합한다. 전체 건수 조회도 별도다. 좋아요뿐 아니라 인증·브랜드·재고·전체 건수까지 **실제로 실행한 SQL**을 세며, 메서드 호출 횟수를 SQL 횟수로 단정하지 않는다.

가격순 목록이 주 측정 대상이다. `latest/likes_desc`, 가격 동점, 다른 페이지, 삭제·사용자별 정보는 결과 보존을 확인하는 별도 검증에 포함한다. 성능 측정에서 API 계약을 바꾸지 않는다.

## 2. 부하를 어떻게 올릴 것인가

### 이미 실행한 예비 탐색

처음 10 RPS에서 과부하가 나타나 1 RPS로 낮췄다. 이후 `1 → 2 → 3 → 5`, 추가로 `6`, 재확인 `5 → 6`, 마지막 `8 RPS`를 실행했다. 대부분 30초, 8 RPS는 20초였다. 5 RPS는 두 번 모두 정상, 6 RPS는 회차에 따라 timeout 발생, 8 RPS는 약 80.75% HTTP 실패였다.

준비·반복 조건이 동일하지 않은 탐색이므로 최대 처리량을 확정한 결과는 아니다. [원본과 수치](report.md)를 보존한다.

### 다음 실험 제안

| 순서 | 실행 방법 | 목적 |
| --- | --- | --- |
| 1. 단일 요청 | 낮은 부하에서 응답·요청당 SQL 수·실제 SQL 확보 | 병목 후보와 결과 기준선 확인 |
| 2. 환경별 탐색 | 1 → 2 → 3 → 5 → 6 → 8 RPS, 단계별 30초 | 로컬에서 관찰한 경계가 새 환경에서도 나타나는지 확인 |
| 3. 추가 탐색 | 8 RPS까지 안정적이면 12 → 18 → 27처럼 약 1.5배 증가 | 새로운 경계 찾기. 최초 불안정 구간에서는 상승 중지 후 사이 값을 재확인 |
| 4. 본 비교 | 선정한 각 단계에서 준비 30초·측정 60초·3회 반복 | 같은 장비·데이터·부하에서 A/B/C 비교 |
| 5. 선택 과부하 | 정상 비교와 분리해 경계 위 단계 실행 | 실패 종류와 부하 중단 후 회복 확인 |

현재 로컬 기준 본 비교 후보는 **1·3·5 RPS**, 경계 관찰은 **6 RPS**, 선택 과부하는 **8 RPS**다. EC2에서는 탐색 결과로 다시 선정한다. 개선 후에도 기존 단계에서 먼저 비교하고, 추가 처리 여력 탐색은 별도로 수행한다.

- k6 `constant-arrival-rate`: 한 iteration에 GET 한 번. 응답이 느려져도 목표 요청 시작률을 유지하도록 한다. VU가 부족하면 미시작 요청이 발생할 수 있다. [공식 설명](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/)
- 초기 탐색은 preAllocated/max VU 100, 요청 timeout 5초, 종료 유예 10초를 제안한다. 높은 부하 탐색 전에는 `목표 RPS × 최대 요청 대기 초`와 검증 비용을 고려해 VU를 확보하고 생성기 자원도 확인한다. VU 변경은 실행 조건에 기록한다.
- timeout·HTTP 오류·미시작이 생기면 다음 단계로 자동 상승하지 않고 원인을 확인한다. 정상 HTTP 응답의 내용 불일치는 즉시 중단한다. 이는 탐색 중단 규칙이며 서비스 SLO가 아니다.
- 각 Run 종료 뒤 Hikari pending 0, 진행 중 서버 요청 정리, 자원 사용과 낮은 부하 응답 회복을 확인한다. 60초 안에 회복되지 않으면 중단하고 증거를 저장한다. 단순히 몇 초 쉬었다는 이유로 회복했다고 보지 않는다.
- 준비 실행은 통계에서 제외한다. 준비 30초로 안정되지 않으면 예비 단계에서 시간을 조정하고 본 비교 전체에 동일 적용한다. 1 RPS·60초는 표본이 적으므로 p99는 참고값이다.
- 본 비교는 A/B를 번갈아 반복해 시간대 편향을 줄이고, 데이터·인덱스·캐시 상태를 매번 확인한다. cache miss는 선택한 실험 키를 요청 전에 비워 확인하고, hit는 미리 채운 뒤 실제 hit 여부를 기록한다. 운영·공유 Redis 전체 삭제는 하지 않는다.

**미정:** 상품 목록의 허용 p95·오류율 목표. week3 주문 API의 p95 1초 목표를 그대로 옮기지 않는다. 우선 비용과 지연 분포를 비교하며 목표 합의 전에는 SLO 충족/위반이라고 판정하지 않는다.

## 3. 시스템 스펙 고정

스펙에 `specId`를 붙여 모든 Run에서 참조한다. 로컬과 EC2는 서로 다른 스펙이다. 로컬 A와 EC2 B의 차이를 코드 개선 효과로 계산하지 않는다.

| 항목 | 확인된 로컬 예비 측정 | 본 실험에서 고정·기록할 값 |
| --- | --- | --- |
| 배치 | Mac에서 앱·k6, Docker에서 MySQL | 예정: EC2 A 앱, EC2 B MySQL·Redis, 로컬 k6. 모니터링 위치도 기록 |
| CPU·RAM | Mac14,2, 8코어, 16 GiB | 호스트/EC2 종류, CPU 아키텍처·vCPU·RAM, Docker CPU·메모리 제한 |
| Docker 자원 | VM 8 CPU·약 7.65 GiB, 다른 컨테이너와 공유 | 각 컨테이너 제한과 호스트 여유, CPU throttling·OOM 여부 |
| 앱 | Java 21.0.12.1, 호스트 JVM 1개 | 컨테이너 1개, Java 실제 버전, 이미지 digest·JAR 해시·JVM 옵션·힙 상한 |
| Tomcat | max threads 200, min spare 10 | 기존 값 유지. max connections 8192·accept count 100도 적용값 기록 |
| Hikari | max 40, min idle 30, 획득 대기 3초 | 기존 값 유지. 풀 크기 변경 실험은 SQL 개선과 분리 |
| MySQL | 8.0.46 | 이미지 digest, 격리 수준, buffer pool 크기, max connections, DDL·인덱스·통계 상태 |
| 저장장치 | Docker 볼륨 | 볼륨 종류·용량·IOPS/처리량 설정, 디스크 여유·I/O 대기 |
| 앱 설정 | `local,perf`, `ddl-auto=none`, SQL 출력 off | Docker는 `local,docker`, `ddl-auto=none`. 로그·계측 수준 동일 |
| Redis | 예비 측정에 캐시 없음 | 캐시 실험에서 버전·메모리·eviction·영속화·TTL·읽는 master/replica endpoint |
| 네트워크 | localhost | 리전·AZ·앱↔DB private 경로·로컬↔앱 경로, HTTP/TLS·연결 재사용, RTT 기준선 |
| 부하 생성기 | k6 2.3.0, 같은 Mac | 버전·CPU/RAM·VU·timeout·스크립트 해시·다른 작업 유무 |
| 실행 소스 | 미커밋 변경 포함 | Git SHA만이 아니라 dirty 여부·실제 이미지/JAR·설정·데이터 해시 |

EC2 인스턴스 종류·힙·컨테이너 제한은 예산과 장비 선정 뒤 확정한다. 아직 숫자를 임의로 적용하지 않는다. 버스트형 장비를 선택하면 CPU credit 상태도 기록한다.

## 4. week3에서 남겼던 Trace와 주변 기록

초기 공통 형식과 후반 계측은 다르다. 모든 실험이 아래 확장 항목을 처음부터 기록한 것은 아니다.

| 기록 위치 | 실제 항목 | 이번에 가져올 부분 |
| --- | --- | --- |
| 기본 HTTP Trace | `runId`, `requestId`, `operation`, `orderId`, `buyerId`, `startNs`, `endNs`, `latencyMs`, `httpStatus`, `outcome`, `errorCode`, `transportError` | 실행/요청 식별·클라이언트 시간·성공/오류. 입력은 brandId/sort/page/size로 교체 |
| 후반 `stockQueries[]` | transactionId·productId·callIndex·operation·found·시작/종료 단조 시각·durationMs·조회 후 TX 완료까지 시간·완료 상태·커넥션 경계 | 요청 안의 호출별 시간과 호출 횟수. 상품 목록·좋아요·재고·브랜드 조회로 적용 |
| 후반 `transactions[]` | transactionId·완료 단조 시각·committed/rolled back | 변경·캐시 무효화 실험에서 commit 경계 추적 |
| 후반 `connectionLeases[]` | leaseId·획득 시작/완료·획득 시간·반환 시각·점유 시간·결과 | 풀 획득 대기와 커넥션을 잡고 있던 시간 분리 |
| 후반 계측 상태 | `measurementStatus`, `measurementReason` | 누락된 계측을 0ms나 성공으로 기록하지 않음 |
| `environment.json` | 시작/종료의 호스트·OS·Java·CPU·메모리·swap·프로세스·Docker/DB 자원·MySQL·k6·시계열 | 사양과 실행 중 자원 압박을 함께 보존 |
| 별도 로그·집계 | Tomcat access log, Hikari/Tomcat 사용량, DB 행 잠금 대기, 요청 시작·완료 건수, 정합성 검증 | Trace와 requestId/시간 구간으로 연결. 시스템 전체 지표를 개별 요청 값으로 오인하지 않음 |

확인 자료: [초기 Trace 원본](../../../week3/experiments/stock-contention/09-pessimistic-write-pool200/results/measure-S1-100rps-01/traces.json), 후반 로컬 산출물 `build/stock-contention-24-timing/warmup/results/*/traces.json` 및 `environment.json`. build와 week3 실험 파일 일부는 Git 추적 밖이므로 재현에 필요한 계측은 별도로 보존해야 한다.

기본 `startNs/endNs`는 이름과 달리 원본의 시간 해상도가 **1ms**였다. 후반 JVM 내부 단조 시각과 같은 시계로 취급하지 않는다.

## 5. 이번에 추가할 트러블슈팅 기록

[회고 글](https://swayloop-log.tistory.com/12)에서는 같은 부하에서도 호스트 자원 상태에 따라 결과가 크게 달랐고, 긴 HTTP 지연의 상당 부분이 Tomcat 처리 시작 기록 이전에 있었다. 이를 바탕으로 아래를 추가한다. 나머지는 이번 상품 조회 과제에 맞춘 제안이다.

| 우선순위·위치 | 추가할 항목 | 확인할 문제 |
| --- | --- | --- |
| 필수·요청 연결 | k6→서버 로그에 동일 `runId/requestId`, 서버 시작·완료 시각, timeout 뒤 서버 완료 여부 | 클라이언트가 포기한 요청이 서버/DB에서 계속 쌓이는지 |
| 필수·클라이언트 Trace | 전체 호출 시간 + k6 blocked/connecting/TLS/sending/waiting/receiving, HTTP·전송 오류 별도 | 로컬→EC2 연결 비용과 응답 대기 구분. waiting 전체를 DB 시간으로 해석하지 않음 |
| 필수·서버 Trace | Tomcat access 처리 시간, 앱 진입/종료, 커넥션 획득·점유 | 서버 진입 전 미설명 구간과 앱·풀 대기 구분 |
| 필수·SQL 진단 | 요청당 SQL 수, 정규화한 SQL 식별자별 횟수·JDBC 실행 시간, 좋아요 집계 소계, SQLState·DB 오류 코드 | N+1인지, 한 SQL이 비싼지, 풀 대기인지 구분 |
| 필수·응답 검증 | 기준 응답 해시·실제 해시·불일치 상품/필드, 검증 결과와 전송 실패를 별도 분류 | 빨라졌지만 다른 결과를 반환하는 변경과 timeout 구분 |
| 필수·Run 시계열 | 1초 단위 시작/완료/실패/미완료 요청 수, Hikari active/idle/pending/timeout, Tomcat busy | 평균에 가려진 요청 누적 시점 찾기 |
| 필수·장비 시계열 | CPU·메모리·swap 입출력·GC pause·컨테이너 throttling·DB I/O·생성기 자원 | 코드 병목과 장비/생성기 압박 구분 |
| 필수·별도 SQL 자료 | 실제 SQL·바인딩 조건, EXPLAIN/ANALYZE, key·예상/실제 rows·loops·정렬 | 읽은 행·반복·정렬 비용의 전후 변화 |
| 캐시 구현 후 | hit/miss/error/bypass, read/write/delete 시간·결과, 원본 호출 수, payload 크기·TTL·읽은 endpoint | hit가 실제 비용을 줄였는지, Redis 장애인지 단순 miss인지 |
| 변경/장애 검증 | commit/rollback → 무효화 시도/성공/실패 → 채움/재조회 순서, 해당 키 또는 revision(선택한 경우) | 정상 최신성·늦은 채움·무효화 실패·복구 근거 |

수집·해석 규칙:

- k6 `http_req_duration`은 sending+waiting+receiving이다. 연결 준비까지 포함한 전체 호출 시간과 별도로 보존한다. [지표 정의](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)
- 각 장비의 UTC 시각·시간 동기화 상태를 기록하고, 구간 길이는 같은 프로세스의 단조 시계로 계산한다. 서로 다른 장비의 타임스탬프를 단순 차감해 큐 대기로 확정하지 않는다.
- 같은 요청의 구간을 연결해 해석한다. `HTTP p95 - SQL p95`로 나머지 대기를 구하지 않는다. 겹치는 TX·SQL·커넥션 시간을 중복 합산하지 않는다.
- JDBC 실행 시간은 DB 엔진의 순수 실행 시간과 다르다. rows/loops는 별도 계획 분석에서 확보한다. 부하 중 요청마다 EXPLAIN ANALYZE를 실행하지 않는다.
- 자원 지표는 우선 1초 간격 수집을 제안한다. 실제 간격·누락을 기록하고 수집 비용을 확인한다. 기존 Prometheus 기본값이 자동으로 바뀌었다고 가정하지 않는다.
- 요청 ID·상품 ID·캐시 키는 로그/Trace에 두고 Prometheus label로 만들지 않는다. metric에는 API·결과·조회 단계처럼 종류가 제한된 값을 쓴다.
- 낮은 부하 진단에서는 SQL 상세를 기록하고, 본 부하에서는 같은 설정의 요약 계측을 유지한다. 계측 on/off의 저부하 비교로 오버헤드도 확인한다. 실패·느린 요청의 상세 보존 방식과 표본율을 기록한다.
- 정상 응답 내용 불일치, HTTP 오류, 연결/timeout, 미시작, 계측 누락을 구분한다. 실패 Run도 삭제하지 않는다.

현재 week4 `products.js/probe.py`는 k6 요약·응답 검증·환경/자원 샘플을 저장한다. **위의 요청별 서버 Trace·SQL 계측과 연결은 아직 구현되지 않았다.** Actuator만 켜면 요청별 SQL Trace가 자동으로 생기는 것은 아니다.

## 6. 과제에서 별도로 확인할 장면

부하 단계 실험만으로 과제 검증이 끝나지 않는다.

| 장면 | 남길 근거 |
| --- | --- |
| DB-only / miss / hit | 같은 결과, hit에서 비싼 원본 SQL·집계 감소. 권위 확인 SQL이 남으면 포함 |
| 관리자 가격 수정·상품/브랜드 삭제·그 밖의 영향 쓰기 | commit 후 새 조회에 반영, 비노출·소유권·과거 주문 유지 |
| rollback | 기존 결과 유지, commit되지 않은 데이터가 캐시에 게시되지 않음 |
| 무효화 실패·이전 조회의 늦은 채움 | 래치/훅으로 순서 통제, 선택한 표시 신선도·복구 정책 검증 |
| 실제 Redis 중단·재시작 | 실제 사용하는 endpoint 중단, 제한된 대기·DB 우회·복구 후 올바른 재채움 |

캐시 경로·TTL·허용 표시 신선도는 별도 설계에서 확정한다. 실패 실험은 작은 통제 부하로 시작하고, 최대 부하 실험과 동시에 바꾸지 않는다. DB 오류를 빈 목록으로 감추거나 주문 판단에 오래된 캐시를 사용하는 것은 허용하지 않는다.

## 7. 이슈 기록 양식

각 이슈는 다음 순서로 남긴다.

1. **증상과 기대:** 어느 RPS·시간 구간에서 무엇이 달랐는지, 기대 결과 또는 합의된 기준.
2. **재현 조건:** experimentId/runId/specId, Git·이미지·데이터 해시, 요청·계측 설정.
3. **증거:** 대표 requestId, 시작→서버 진입→풀 획득→SQL→서버 완료→클라이언트 완료의 확인 가능한 구간, 같은 시간대 자원 그래프·원본 로그.
4. **관찰과 가설:** 직접 측정한 사실과 추정 원인을 분리. 미측정 구간은 미상으로 표시.
5. **변경과 재검증:** 한 번에 바꾼 요소, 같은 조건의 전후 결과·응답 일치·회귀 결과, 남은 한계.

Run별 `environment.json`, `result.json`, `traces.jsonl`, 서버 로그, SQL 계획/통계, 응답 검증 결과를 연결하는 구조를 제안한다. 그래프 이미지만 남기지 않고 원본 시계열과 집계 기준도 보존한다.

## 8. 다음 구현에서 바꿀 파일과 확인 방법

| 대상 | 변경 책임 | 확인 방법 |
| --- | --- | --- |
| `docs/week4/experiments/preliminary/products.js` | 요청 ID·클라이언트 구간·오류/불일치 분리 | 단일 요청·잘못된 응답·timeout에서 기록 확인 |
| `docs/week4/experiments/preliminary/probe.py` | 고정 스펙·반복·회복 조건·원본 수집·실패 Run 보존 | 작은 Run으로 준비/측정 분리·중단·집계 확인 |
| 신규 `docs/week4/experiments/preliminary/support/` 계측 설정/코드 | 서버 요청·JDBC·커넥션 연결, 운영 경로 영향 제한 | 구현 방식 확정 후 파일명·계측 경계와 관련 테스트 먼저 제안 |
| 실험 전용 monitoring/Compose 설정 | 수집 간격·자원 설정·실제 endpoint 고정 | 적용 설정과 수집값 대조 |

이번 변경은 이 계획 문서만 추가한다. 실행 코드 변경 시 관련 테스트와 기존 lint·ArchUnit을 확인하고, 과제 완료 시 상품·브랜드·좋아요·주문 회귀를 함께 실행한다.
