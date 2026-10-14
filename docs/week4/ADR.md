# Redis 캐시 전략 ADR

## 목표 설계

RDB가 원본이고 Redis는 Cache Aside로 사용한다. 쓰기는 RDB 커밋 후 Redis 캐시를 삭제하는 Write Invalidate 방식으로 전환한다.

| 항목 | 결정 |
| --- | --- |
| TTL | 전체 미설정 |
| 메모리 관리 | allkeys-lru / maxmemory 용량 미정 |
| Redis 장애 | RDB fallback |
| DB 보호 | Bulkhead 우선 적용, 허용 동시 요청 수 미정 |
| 추가 검토 | Circuit Breaker, 짧은 Redis timeout, Rate Limiter, Single Flight |
| 정합성 | 무효화 실패 재시도·복구 및 늦은 재적재 대응 방식 미정 |
| 부하 거절 | HTTP 응답·재시도 정책 미정 |
| 정상 상태 목표 | 1,700 RPS, p99 ≤ 200ms |

Redis 장애 중 서비스 지속·DB 보호는 정상 상태 SLO와 별도로 검증한다. 이번 표가 목표 설계이며, 아래 기존 구현을 이 설계로 전환하는 코드는 아직 적용하지 않았다.

### 검증할 흐름

| 상황 | 확인할 지표 |
| --- | --- |
| 정상 hit | 실제 전달 RPS·성공률·p99, Redis hit/miss·명령 지연·CPU·메모리, 원본 SQL 실행 수 |
| LRU로 miss 증가 | evicted_keys 증가, miss 비율, DB로 넘어간 요청 수·SQL 수·지연·CPU·I/O·Hikari active/pending |
| Redis 장애 | timeout·오류, fallback 시도·허용·거절 수, Bulkhead 실행 중 요청 수·대기 시간, DB 자원·p99 |
| 장애 복구 | 재적재 요청 수·동시성, DB 부하, 무효화 재처리·옛 값 재노출 여부 |

요청 흐름은 `전체 요청 → hit / miss / Redis 오류 → DB 진입 시도 → 허용 / 거절 → 완료 / 실패`로 계측한다. 캐시 miss뿐 아니라 인증·revision 등 부가 DB 호출도 따로 집계해 실제 DB 부하를 빠뜨리지 않는다. 성공 응답만의 p99와 전체 성공률·거절률·미시작 요청 수를 함께 기록한다. 제한으로 요청을 버려 낮아진 p99만으로 SLO 달성을 선언하지 않는다.

## 현재 구현 기록

아래는 커밋 시점의 구현이다. **DB revision 및 좋아요 1분 TTL을 사용하며, 위 목표 설계와 다르다.**

상품 기본 정보와 좋아요 수·품절 상태를 API별로 분리한다. 프론트가 `productId`로 합친다.

| 읽기 경로 | 내용을 변경하는 쓰기 동작 | 키 본문 | TTL | 무효화 |
| --- | --- | --- | --- | --- |
| 상품 목록 최신순·가격순 | 상품 CUD / 브랜드 UD | `v2:products?sort={sort}&brand={brand}&page={page}&size={size}` | 없음 | catalog revision |
| 상품 목록 좋아요순 | 위 동작 / 좋아요 등록·취소 | 위 키, sort=likes_desc | 1분 | 상품·브랜드는 revision / 좋아요는 만료 |
| 상품 상세 | 상품·브랜드 UD | `v2:products/{id}` | 없음 | catalog revision |
| 내 좋아요 목록 | 내 좋아요 등록·취소 / 상품·브랜드 UD | `v2:users/{userId}/likes?page={page}&size={size}` | 없음 | catalog + 사용자 revision |
| 관리자 브랜드 목록 | 브랜드 CUD | `v2:admin:brands?page={page}&size={size}` | 없음 | catalog revision |
| 좋아요 수 일괄 조회 | 좋아요 등록·취소 / 상품·브랜드 D | `v2:products/like-counts?ids={ids}` | 1분 | 좋아요는 만료 / 상품·브랜드는 revision |
| 품절 상태 일괄 조회 | 재고 변경·주문 확정 / 상품·브랜드 D | `v2:products/stocks?ids={ids}` | 없음 | catalog + stock revision |

C 생성, U 수정, D 삭제. 모든 행에 캐시를 적용한다. 키 생성은 `CacheKeys`에서 관리하며 실제 Redis 키는 `캐시이름::키본문:rev=...`이다. 브랜드 미지정은 `all`, ids는 중복 제거·오름차순, 생략한 페이지·정렬 조건은 기본값으로 정규화한다.

## 읽기와 쓰기

- 읽기: 요청자·소유권 확인 → DB revision 조회 → `@Cacheable` → miss면 DB 조회·저장. Redis 오류는 DB로 우회하며 오류 지표를 남긴다.
- 쓰기: 기존 관리자·좋아요·주문 경로에서 데이터와 revision을 **같은 DB 트랜잭션**으로 변경한다. 커밋 이후 요청은 새 revision 키를 사용한다. 롤백은 revision도 취소한다.
- 늦은 재삽입: 변경 전 조회가 뒤늦게 캐시에 저장해도 이전 revision 키라 이후 요청이 사용하지 않는다. Redis 삭제 성공 여부에 의존하지 않는다.
- 삭제·사용자 경계·주문 판단은 TTL 지연 대상이 아니다. 좋아요 표시 숫자·순위만 1분 TTL을 사용한다. TTL은 캐시 저장 시점부터이며 모든 장애·동시 요청에서 전역 1분 최신성을 보장하는 것은 아니다.

## 비용과 적용 조건

- catalog·stock revision은 공통 범위이므로 한 상품 변경도 관련 목록 전체를 논리적으로 무효화한다. hit에도 revision DB 조회가 남고 동시 쓰기는 공통 revision 행에서 경합할 수 있다.
- 동시에 발생한 cache miss를 하나로 합치는 처리는 아직 없다. 인기 키의 동시 재채움 비용은 혼합 부하에서 확인한다.
- 이전 키는 즉시 물리 삭제하지 않는다. TTL 없는 키를 운영하려면 Redis `maxmemory`와 `allkeys-lru` 또는 `allkeys-lfu` 선택이 필요하다. 용량·정책은 아직 미정이며 EC2에 적용하지 않았다.
- `catalog-cache` 프로필로 활성화한다. 기존 고정 데이터 실험용 `experiment.latest-cache.enabled`와 동시 사용은 금지한다.
- 활성화·재활성화 전에 쓰기를 멈추고 [revision SQL](migrations/05-add-read-cache-revision.sql)을 적용한다. 캐시를 끈 동안 발생한 변경까지 무효화하도록 catalog revision도 갱신한다. 혼합 ON/OFF 쓰기 인스턴스로 운영하지 않는다.
- 신규 경로·직접 SQL 적재도 revision 갱신을 누락하면 안 된다. 모든 쓰기가 저장소 어댑터의 save/delete를 거치는 현재 경로를 기준으로 한다.

## 프론트 계약

기본 목록·상세·내 좋아요 응답에는 `id`, `name`, `price`, `brand`만 있다. 받은 ID로 `/api/v1/products/like-counts?ids=...`와 `/api/v1/products/stocks?ids=...`를 각각 호출한다. 두 API도 기존 사용자 확인을 요구한다. 재고 응답 전에는 품절 여부를 미확인 상태로 보여 주고, 실패를 재고 있음으로 취급하지 않는다. 주문 확정은 기존 DB 검증을 유지한다.

ids는 중복 제거 전 1~100개 양수 ID만 허용한다. 삭제·없는 상품은 제외된다. 기존 프론트와 함께 계약을 전환해야 하며 현재 EC2 배포는 이전 계약이다.
