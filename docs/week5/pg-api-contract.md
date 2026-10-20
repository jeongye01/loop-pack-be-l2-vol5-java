# 테스트 PG API 계약

## 공통

| 항목 | 계약 |
| --- | --- |
| Base URL | `http://127.0.0.1:18089` |
| 요청 헤더 | 생성·조회 API 모두 `X-USER-ID: 135135` |
| 본문 형식 | JSON. 본문이 있는 요청·응답은 `Content-Type: application/json` |
| 응답 형식 | PG 거래 객체 또는 배열을 직접 반환. commerce의 `meta`, `data` 형식을 사용하지 않음 |
| 외부 상태 | `PENDING`, `SUCCESS`, `FAILED`. `UNKNOWN`은 commerce 내부 판단이며 PG 상태가 아님 |
| 주문 식별자 | PG의 `orderId`는 commerce의 `providerOrderId`. 내부 주문 ID와 구분하며 생성·조회에 같은 값 사용 |
| 생성 중복 | 호출자 멱등 키를 받지 않음. 같은 `orderId`로 새 POST를 보내도 중복 제거를 보장하지 않음 |
| 시나리오 선택 | 테스트가 WireMock 설정으로 선택. 고객 요청에 동기·비동기 모드 필드를 추가하지 않음 |

## PG-01. 결제 생성

| 항목 | 계약 |
| --- | --- |
| 요청 | `POST /api/v1/payments` |
| 입력 | body `orderId`, `cardType`, `cardNo`, `amount`, `callbackUrl` |
| 비동기 접수 | `202`, 거래 객체의 `status=PENDING` |
| 동기 완료 | 거래 객체의 `status=SUCCESS` 또는 `FAILED`. **HTTP 코드는 합의 대기** |
| 생성 전 확정 거절 | `422`, 아래의 전용 오류 객체 |

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `orderId` | string | 6자 이상 PG 주문 식별자 |
| `cardType` | string | 합성 카드 타입 `TEST` |
| `cardNo` | string | 합성 카드 번호 `0000-0000-0000-0000` |
| `amount` | integer | 저장된 주문에서 결정한 원화 결제 금액 |
| `callbackUrl` | string | 서버 설정으로 고정한 로컬 callback URL |

### 거래 객체

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `transactionKey` | string | PG 거래 한 건의 식별자. 같은 거래 조회에서 유지 |
| `orderId` | string | 생성 요청의 PG 주문 식별자 |
| `amount` | integer | 생성 요청의 금액 |
| `status` | string | `PENDING`, `SUCCESS`, `FAILED` |

### 거래 생성 전 확정 거절

| 필드 | 타입 | 값 |
| --- | --- | --- |
| `code` | string | `TEST_PG_REJECTED_BEFORE_CREATE` |
| `orderId` | string | 요청의 PG 주문 식별자 |
| `amount` | integer | 요청 금액 |
| `transactionCreated` | boolean | `false`. 이후에도 해당 요청의 거래를 생성하지 않음 |

## PG-02. 거래 단건 조회

| 항목 | 계약 |
| --- | --- |
| 요청 | `GET /api/v1/payments/{transactionKey}` |
| 입력 | path `transactionKey` |
| 성공 | `200`, 거래 객체 |
| 없음 | `404`. 오류 본문에 의존하지 않음 |

## PG-03. 주문별 거래 조회

| 항목 | 계약 |
| --- | --- |
| 요청 | `GET /api/v1/payments?orderId={providerOrderId}` |
| 입력 | query `orderId`. URI builder로 인코딩 |
| 성공 | `200`, 거래 객체 배열. 없는 경우 `[]` |

## PG-04. 결제 완료 웹훅

| 항목 | 계약 |
| --- | --- |
| 요청 | `POST {callbackUrl}` |
| 입력 | body `providerOrderId`, `transactionKey`, `status` |
| 완료 알림 상태 | `SUCCESS` 또는 `FAILED` |
| 수신 응답 | [commerce callback 계약](../week2/api-contract.md)에 따름 |

## WireMock 시나리오

| 상황 | 생성 응답 | 후속 조회·웹훅 | 검증할 결과 |
| --- | --- | --- | --- |
| 동기 성공 | `SUCCESS` 거래 | 선택적으로 동일 성공 웹훅 | 주문 확정, 예약 소비, 추가 차감 없음 |
| 동기 실패 | `FAILED` 거래 | 같은 실패 조회 | 예약 한 번 반환 |
| 비동기 성공 | `202 PENDING` | 완료 웹훅 → 조회 `SUCCESS` | 접수 때 미확정, 성공 확인 후 확정 |
| 비동기 실패 | `202 PENDING` | 완료 웹훅 → 조회 `FAILED` | 예약 한 번 반환 |
| 생성 전 확정 거절 | 전용 `422` | 거래 0건, 완료 웹훅 없음 | 실패 종결, 예약 반환, 생성 재시도 없음 |
| 거래 생성 뒤 응답 유실 | 거래 생성 후 응답 지연·연결 단절 | 웹훅 또는 주문별 조회로 같은 거래 확인 | UNKNOWN에서 복구, 생성 추가 호출 없음 |
| 성공 알림과 조회 불일치 | `202 PENDING` | 성공 알림을 보내도 조회는 `PENDING` | 주문 미확정, 예약 유지 |
| 웹훅 누락 | `202 PENDING` 또는 응답 유실 | 대사 조회에서 최종 결과 | 기존 거래로 복구 |
| 조회 불가 | 거래 존재 여부와 독립 | 지연·오류 또는 회로 차단 | 저장된 결과 보존, 임의 실패·반환 없음 |
| 잘못된 거래 결과 | 정상 요청 | 다른 주문·거래·금액 응답, 복수 거래 | 결과 적용 거절·충돌 보존 |
| 중복·순서 변경 | 초기 응답 지연 가능 | 웹훅 먼저, 대사 먼저, 중복·동시 적용 | 종결 역행·중복 확정·중복 반환 없음 |
| 외부 성공 뒤 내부 저장 실패 | 성공한 거래 유지 | 같은 거래 재조회 | 내부 rollback 후 한 번만 반영 |
