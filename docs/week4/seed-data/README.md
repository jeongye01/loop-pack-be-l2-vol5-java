# Catalog seed data

작성일: 2026-10-12

실제 상품 모델에 맞는 CSV를 생성·검증하고, 격리된 MySQL DB에 반복 적재한다. 모든 명령은 저장소 루트에서 실행한다. Java 21, 프로젝트 Gradle wrapper, Python 3.11 이상, MySQL 8 클라이언트, Docker가 필요하다.

현재 서울 앱은 `skewed-1m-v1` 데이터셋의 `perf_skewed_1m_v1` DB를 사용한다. 상품 100만·브랜드 1천·사용자 24만·좋아요 69만·재고 100만 건이다. [분포와 적재 결과](million-product-plan.md)

편중 버전 생성·검증 명령(출력 폴더가 없을 때):

```bash
./gradlew -I docs/week4/seed-data/scripts/seed.gradle :apps:commerce-api:generateCatalogCsv \
  -PdatasetDir=docs/week4/seed-data/skewed-1m-v1 -PseedValue=42 -PdatasetProfile=skewed-1m
python3 docs/week4/seed-data/scripts/validate.py docs/week4/seed-data/skewed-1m-v1 --write-manifest
```

100만 상품 버전의 CSV는 크기가 커서 Git에서 제외한다. 생성 코드·조건·체크섬은 추적하며 위 명령으로 동일하게 재생성할 수 있다.

아래는 보존한 기존 `uniform-10k-v1`의 설명과 로컬 실행 예시다.

## 기존 데이터셋

원본 경로: `docs/week4/seed-data/uniform-10k-v1/`

| 파일 | 행 수 |
| --- | ---: |
| `brands.csv` | 100 |
| `users.csv` | 10,000 |
| `products.csv` | 10,000 |
| `stocks.csv` | 10,000 |
| `likes.csv` | 999,493 |

- 브랜드마다 상품 100개. 상품명은 전체에서 고유하다.
- 가격 1~99,999,999원, 재고 0~99,999,999개에서 균등 랜덤 생성한다.
- 상품당 좋아요 0~200개에서 균등 랜덤 생성하고, 사용자는 중복 없이 선택한다. 총량을 100만 개로 보정하지 않는다.
- seed는 42, 기준 시각은 `2026-01-01 00:00:00.000000` UTC다. 이름은 Datafaker 2.5.1·영어 로케일로 생성한다.
- CSV 5개 합계는 78,102,634 bytes(약 74.5 MiB)다.
- `generation.json`은 생성 조건, `manifest.json`은 실제 건수·분포·파일 및 행 체크섬을 보관한다.
- UTF-8, LF, 헤더 포함, 문자열의 큰따옴표는 두 번 써서 이스케이프한다. 빈 `deleted_at` 필드는 SQL NULL로 변환한다.
- CSV에는 엔티티의 원본 필드만 있다. DB가 계산하는 `active_name_key`, 응답의 `likeCount`·`soldOut`은 넣지 않는다.

## 1. 실험용 MySQL 시작

```bash
docker compose -f docs/week4/seed-data/compose.yml up -d --wait
```

개발 DB의 3306 포트와 분리된 `127.0.0.1:13307`을 사용한다. MySQL 인스턴스 하나에 버전별 DB를 추가하는 구조다. 최초 DB는 `perf_uniform_10k_v1`이며, 로컬 계정은 `application/application`이다. 볼륨에 데이터를 유지한다.

## 2. 빈 DB에 테이블 생성

```bash
./gradlew -I docs/week4/seed-data/scripts/seed.gradle :apps:commerce-api:initCatalogSchema
```

`CatalogSchemaInit.java`가 실제 앱의 엔티티 매핑으로 테이블을 만든 뒤 앱을 종료한다. `perf_*` 이름이 아니거나 테이블이 이미 있는 DB에서는 실행을 거부한다. 재사용할 DB에서는 이 단계를 반복하지 않는다.

## 3. CSV 생성·검증

저장된 `uniform-10k-v1`을 적재할 때는 생성하지 않고 검증만 실행한다.

```bash
python3 docs/week4/seed-data/scripts/validate.py docs/week4/seed-data/uniform-10k-v1
```

새 버전 생성 예시:

```bash
./gradlew -I docs/week4/seed-data/scripts/seed.gradle :apps:commerce-api:generateCatalogCsv \
  -PdatasetDir=docs/week4/seed-data/uniform-10k-v2 -PseedValue=43
python3 docs/week4/seed-data/scripts/validate.py docs/week4/seed-data/uniform-10k-v2 --write-manifest
```

기존 출력 폴더·manifest는 덮어쓰지 않는다. Datafaker는 별도 생성용 Gradle 구성에서만 사용하며 앱 실행 의존성에는 포함하지 않는다. 생성 시점에 DB나 JPA를 호출하지 않는다. 검증기는 실제 CSV를 다시 읽어 필드·참조·고유성·건수·분포를 확인한다.

## 4. CSV 적재

```bash
docs/week4/seed-data/scripts/load.sh docs/week4/seed-data/uniform-10k-v1 perf_uniform_10k_v1
```

- 대상 테이블에 데이터가 있으면 중단한다. 기존 버전을 자동으로 비우지 않는다.
- 스키마 제약을 확인하고 브랜드·사용자 → 상품 → 재고·좋아요 순서로 `LOAD DATA LOCAL INFILE`을 실행한다.
- 경고·누락이 없어야 하고, 모든 적재 필드의 ID 순서별 행 체크섬과 건수가 CSV와 같아야 commit한다. 실패하면 적재 트랜잭션을 rollback한다.
- 연결은 `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PWD`로 변경할 수 있다. 기본값은 위 실험용 MySQL이다.
- 적재 결과는 `load-reports/<DB명>.json`에 저장한다.

## 5. 해당 DB로 앱 실행

```bash
./gradlew :apps:commerce-api:bootRun \
  --args='--spring.profiles.active=local,perf --server.port=18080 --management.server.port=0'
```

`application-perf.yml`은 실험 DB 연결과 `ddl-auto=none`을 설정한다. 프로필은 `local,perf` 순서로 지정한다. 다른 버전을 사용할 때는 `PERF_DB_NAME`을 바꾼다. 나머지 연결값은 `PERF_DB_HOST`, `PERF_DB_PORT`, `PERF_DB_USER`, `PERF_DB_PASSWORD`로 지정한다.

```bash
curl -H 'X-USER-ID: 1' \
  'http://127.0.0.1:18080/api/v1/products?brandId=7&sort=price_asc&page=0&size=20'
```

새 버전의 DB는 같은 MySQL에서 먼저 생성하고 `application` 계정에 해당 DB 권한을 부여한다. 예를 들어:

```bash
docker compose -f docs/week4/seed-data/compose.yml exec -e MYSQL_PWD=root mysql mysql -uroot -e \
  "CREATE DATABASE perf_uniform_10k_v2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON perf_uniform_10k_v2.* TO 'application'@'%';"
PERF_DB_NAME=perf_uniform_10k_v2 ./gradlew -I docs/week4/seed-data/scripts/seed.gradle :apps:commerce-api:initCatalogSchema
```

생성기·검증기는 균등 버전과 `skewed-1m` 편중 버전을 구분해 검증한다. 기존 데이터셋은 유지한다.

## 확인 결과

- 같은 seed로 별도 폴더에 재생성한 CSV 5개의 SHA-256이 원본과 모두 일치했다.
- 5개 테이블의 모든 적재 필드·건수를 비교한 뒤 commit했다.
- 앱을 `none` 설정으로 실행한 뒤에도 데이터가 유지됐다.
- 브랜드 7·가격순·첫 페이지의 실제 HTTP 응답을 CSV로 계산한 상품 ID·이름·가격·브랜드·좋아요 수·품절 여부·페이지 정보와 비교해 일치를 확인했다.

스키마 제약 적용·오류 처리 검증은 [migration README](../migrations/README.md)에 기록한다.
