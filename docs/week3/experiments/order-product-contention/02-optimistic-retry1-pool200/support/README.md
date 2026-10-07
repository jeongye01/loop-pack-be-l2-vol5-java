# 낙관적 버전 락 실행 지원

저장소 루트에서 실행한다. Java 21·Docker·Python 3·k6가 필요하다.

## 환경

- DB: `commerce-serializable-mysql`, `127.0.0.1:13308`, 전용 DB `commerce_experiment`
- 애플리케이션: `127.0.0.1:18080`, 원본 코드 + 실험 `config.json`
- Hikari: 최대 200·최소 유휴 200·대기 3초. MySQL `max_connections`는 300 이상으로 설정한다. 테스트는 기존 최대 10·최소 유휴 5.
- DB 기본 격리 수준(REPEATABLE-READ)과 JPA `@Version`을 적용하고 낙관적 충돌을 1회 재시도한다.
- SQL 출력은 끈다. HTTP 500의 원인은 Tomcat 접근 로그의 요청 ID·스레드·시간과 서버 오류 로그를 연결한다. 상관관계가 없는 오류는 `unclassified`로 남긴다.
- 실험은 기존 Swagger DB를 사용하지 않는다. 데이터 초기화는 전용 DB의 실험 테이블에만 적용된다.

```bash
docker run -d --name commerce-serializable-mysql -p 127.0.0.1:13308:3306 \
  -e MYSQL_ROOT_PASSWORD=lab-root -e MYSQL_USER=application \
  -e MYSQL_PASSWORD=application -e MYSQL_DATABASE=commerce_experiment \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci --max_connections=300
./gradlew :apps:commerce-api:bootJar
python3 docs/week3/experiments/order-product-contention/02-optimistic-retry1-pool200/support/server.py
```

실행 로그·PID·fixture·k6 원본은 `build/serializable-experiment/`에 저장한다. 서버를 시작하면 Hibernate의 기존 local 설정에 따라 전용 DB의 테이블이 재생성된다. 재실행 전에 해당 실험 서버가 종료됐는지 확인한다.

## 동시성 테스트

```bash
python3 docs/week3/experiments/order-product-contention/02-optimistic-retry1-pool200/support/run_tests.py
```

기존 4개 테스트를 수정하지 않는다. Gradle init script로 실험용 `IsolationProbeTest`를 추가해 실제 커넥션의 격리 수준도 매회 검증한다. 실패한 assertion은 JSON에 그대로 기록한다.

## HTTP 부하

```bash
# S1: 부하 3단계 × 3회, 매회 준비 30초 + 측정 60초
python3 docs/week3/experiments/order-product-contention/02-optimistic-retry1-pool200/support/batch.py --scenario S1

# 결과 표 재생성
python3 docs/week3/experiments/order-product-contention/02-optimistic-retry1-pool200/support/report.py
```

S2·S3는 합의한 2종 주문 비율을 `--two-item-share`로 전달해야 실행된다. 생성기 VU는 200개로 시작하며 예비 실행의 미시작 요청 수로 부족 여부를 확인한다. 요청은 고정 시드 `20261005`로 생성한다. 품목별 가격은 4,000원, 사용자 초기 잔액은 100,000원이다.

Run ID가 이미 있으면 결과를 덮어쓰지 않는다. HTTP 지연은 호출 직전부터 응답 수신까지 밀리초 정밀도로 측정한다. 품절 전후 경계는 첫 품절 응답 관찰 시각이며 정확한 DB 소진 시각은 아니다. 실험 중 다른 테스트·빌드는 실행하지 않는다.
