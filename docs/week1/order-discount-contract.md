# 주문 할인 계약

## 기존 HTTP 동작 관찰

관찰 기준: Guide 고정 커밋의 `/api/v1/examples`를 `RANDOM_PORT + TestRestTemplate`로 요청한 결과

| 입력 분류 | 실제 HTTP 입력 | HTTP status | `meta.result` | error code | `data` |
|---|---|---:|---|---|---|
| 정상 숫자 | 저장된 Example ID로 `GET /api/v1/examples/{id}` | `200 OK` | `SUCCESS` | `null` | `{"id": 저장된 ID, "name": "예시 제목", "description": "예시 설명"}` |
| 문법 오류 | `GET /api/v1/examples/abc` | `400 BAD_REQUEST` | `FAIL` | `Bad Request` | `null` |
| 미존재 | `GET /api/v1/examples/-1` | `404 NOT_FOUND` | `FAIL` | `Not Found` | `null` |
| 미매핑 | `GET /api/v1/unmapped` | `404 NOT_FOUND` | `FAIL` | `Not Found` | `null` |
