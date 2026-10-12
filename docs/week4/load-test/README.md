# 상품 목록 RPS 예비 측정

작성일: 2026-10-13

본 실험의 부하 단계를 정하기 위한 짧은 로컬 탐색이다. 애플리케이션·MySQL·k6가 같은 Mac 자원을 공유한다. 결과는 운영 환경의 최대 처리량을 뜻하지 않는다.

## 고정 요청

```http
GET /api/v1/products?brandId=7&sort=price_asc&page=0&size=20
X-USER-ID: 1
```

`uniform-10k-v1` 데이터셋을 사용한다. 조회 결과 전체를 CSV로 계산한 값과 사전 비교하고, 부하 중에는 모든 응답의 상품·가격·좋아요 수·정렬·페이지 정보를 기준 응답과 비교한다. JSON 객체 필드 순서는 비교 대상에서 제외한다.

## 실행

실험 DB가 실행 중인 상태에서 현재 앱을 실행한다. 스키마와 데이터는 재생성하지 않는다.

```bash
./gradlew :apps:commerce-api:bootRun \
  --args='--spring.profiles.active=local,perf --server.port=18080 --management.server.port=18081 --spring.jpa.hibernate.ddl-auto=none --spring.jpa.show-sql=false'

python3 docs/week4/load-test/probe.py --rates 1,2,3,5 --duration 30 --warmup 15 --vus 100
```

- week3의 k6 `constant-arrival-rate`, 준비 실행과 측정 분리, JSON 원본 저장 방식을 재사용했다.
- 요청 1회가 iteration 1회다. 클라이언트 timeout은 5초, 종료 유예는 10초다. 단계 사이 5초를 쉰다.
- 최초 준비 실행 후 단계별 부하를 순차 적용한다. 같은 단계 반복은 `--rates 3,3`처럼 지정한다.
- 오류·응답 불일치·미전송 요청이 1%를 넘으면 더 높은 단계로 진행하지 않는다. 이 기준은 예비 실험 중단 조건이며 서비스 SLO가 아니다.
- Hikari 최대 40·최소 유휴 30·연결 대기 3초, Tomcat 최대 스레드 200을 유지한다.
- DB 초기화, 재고 변경, 주문 생성, 인덱스·쿼리 최적화는 수행하지 않는다.
- 부하 후 앱에 요청이 남아 있으면 연결 풀의 active·pending이 0이 된 뒤 다음 실행을 시작한다.

## 결과

### EC2 예비 탐색

```bash
python3 docs/week4/load-test/ec2_probe.py
```

현재 배포한 앱 `3.106.241.33`과 DB `16.176.204.86` 전용 실행 파일이다. SSH 키 경로는 파일 상단의 `KEY`를 사용한다. `products.js`의 동일 요청·5초 timeout·응답 검증과 `probe.py`의 CSV 기준 검증을 재사용한다. DB 초기화나 인덱스 변경은 하지 않는다.

- 로컬 k6 → 시드니 EC2의 공개 HTTP 경로로 1 RPS 준비 15초 후 1·2·3·5·6·8 RPS를 각각 30초 실행한다. VU는 100개다.
- 이번 EC2 실행은 HTTP 실패·응답 검증 실패·미전송이 **한 건이라도 있으면** 부하 상승을 멈춘다. 기존 로컬 실행의 1% 규칙과 구분한다.
- 각 단계 전과 마지막에 Hikari active/pending 0과 기준 응답 일치를 확인한다. 회복 확인 대기는 60초로 제한한다.
- 자원 기록은 로컬 생성기 프로세스와 **EC2 두 서버**의 자원이다. SSH 수집에 걸리는 시간이 있어 일정한 1초 간격 자료가 아니며 실제 시각을 사용한다.
- `results/ec2-<실행 시각>/`에 보관한다. 앱 JAR·이미지·스크립트·데이터 해시, 단계별 결과, 서버 로그, 회복 결과를 포함한다.
- 기존 `invalid_response`는 HTTP/전송 실패도 포함하므로 이를 모두 정상 응답의 내용 불일치로 해석하지 않는다. 요청별 SQL/서버 Trace 수집은 아직 없다.

### 결과 파일

`results/<실행 시각>/`에 환경·기준 응답·단계별 결과를 저장한다.

| 파일 | 내용 |
| --- | --- |
| `environment.json` | 소스 상태, 장비·도구 버전, 데이터셋 manifest 체크섬, 실행 조건 |
| `expected.json` | CSV와 비교한 기준 응답 |
| `results.json` | 준비 실행을 제외한 단계별 요약 |
| 단계별 `summary.json`, `k6.log` | k6 원본 결과·오류 기록 |
| 단계별 `resources.json` | 실행 중 로컬 프로세스 CPU·메모리, 실험 MySQL 컨테이너 상태 |
| 단계별 `metrics-before.txt`, `metrics-after.txt` | Actuator Prometheus 지표 스냅샷 |

p50·p95·p99는 k6의 `http_req_duration`이며 요청 전송·응답 대기·수신 시간을 포함한다. 연결 수립 시간은 별도 지표다. week3의 `Date.now()` 호출 전후 시간과 동일한 지표가 아니다. `rpsIncludingDrain`은 k6의 `http_reqs.rate`로 종료 시 남은 요청 처리 시간의 영향도 받는다. 목표 RPS와 구분한다. 타임아웃이 많을 때 p95가 5초인 것은 앱이 5초 안에 처리했다는 뜻이 아니다.

현재 목적은 부하 범위 탐색이다. 각 단계의 표본이 적어 특히 p99는 참고값이며, 정식 비교에서는 준비 시간·반복 횟수·측정 시간을 별도로 고정한다.
