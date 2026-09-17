# commerce-api API 응답 계약

[API 계약](./api-contract.md)의 성공·실패 응답이 따르는 형식, 필드, 오류 코드다. 근거는 [요구사항 문서](./requirements.md)의 요구사항 ID와 정책 ID를 가리킨다.

## 응답 형식

모든 응답(관리자 `403` 제외)은 공통 형식을 쓴다.

```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": { } }
```

- 성공은 `201 Created` 또는 `200 OK`이고 `meta.result`는 `SUCCESS`이다. 새 대상을 만든 요청은 `201`, 나머지는 `200`이다. 돌려줄 값이 없으면 `data`는 `null`이다.
- 실패는 `오류 코드`의 상태 코드이고 `meta.result`는 `FAIL`, `meta.errorCode`와 `meta.message`를 채운다.

## 목록 응답

목록을 돌려주는 성공 응답의 `data`는 다음 필드를 가진다. 페이지 요청 규칙은 [API 계약](./api-contract.md)의 `목록`에 있다.

| 필드 | 뜻 |
| --- | --- |
| `content` | 항목 배열 |
| `page` | 요청한 페이지(0부터) |
| `size` | 요청한 페이지 크기 |
| `totalElements` | 조건에 맞는 전체 항목 수 |

## 응답 필드

| 이름 | 필드 | 쓰는 API |
| --- | --- | --- |
| 고객 브랜드 | `id`, `name` | C-01 |
| 고객 상품 | `id`, `name`, `price`, `brand`(`id`, `name`), `likeCount`, `soldOut` | C-02, C-03, C-06 |
| 주문 상세 | `id`, `status`, `items`(`productId`, `productName`, `quantity`, `unitPrice`, `amount`), `totalAmount`, `payment`(`amount`, `paidAt`) | C-09, C-10, C-12 |
| 주문 요약 | `id`, `status`, `itemCount`, `totalAmount`, `paymentAmount` | C-11 |
| 관리자 브랜드 | `id`, `name`, `deleted` | A-01 ~ A-04 |
| 관리자 상품 | `id`, `name`, `price`, `brand`(`id`, `name`), `stock`, `deleted` | A-06 ~ A-09, A-11 |
| 관리자 주문 상세 | 주문 상세 + `buyerId` | A-13 |
| 관리자 주문 요약 | 주문 요약 + `buyerId` | A-12 |

- `likeCount`는 그 상품의 좋아요 관계를 세어 얻는다. 상품에 저장하지 않는다. (R-LIKE-05)
- 고객 상품은 재고 수량 대신 품절 여부(`soldOut`)를 보여 주고, 관리자 상품은 좋아요 수 대신 재고와 삭제 여부를 보여 준다. (R-ACCESS-06, P-CATALOG-01, P-ADMIN-07, P-ADMIN-09)
- `status`는 `DRAFT` 또는 `CONFIRMED`이다. `DRAFT` 주문의 `payment`와 `paymentAmount`는 `null`이다. (R-ORDER-03, R-ORDER-12, P-ORDER-06)
- 주문 품목의 `productName`과 `unitPrice`는 주문을 생성할 때의 값이며, 상품이 수정되거나 삭제되어도 바뀌지 않는다. (P-ORDER-03, P-ORDER-07)

금액, 잔액, 가격은 원 단위 정수이고, 표현 범위는 64비트 정수이다.

## 오류 코드

| 상태 | `meta.errorCode` | 뜻 | `meta.message` |
| --- | --- | --- | --- |
| `400` | `INVALID_REQUEST` | 요청의 형식이나 값이 규칙을 벗어났다. | 요청 형식이 올바르지 않습니다. 입력값을 확인해 주세요. |
| `401` | `USER_NOT_IDENTIFIED` | 고객을 식별할 수 없다. | 사용자를 확인할 수 없습니다. |
| `404` | `BRAND_NOT_FOUND` | 브랜드가 없거나, 고객 조회에서 삭제된 브랜드이다. | 브랜드를 찾을 수 없습니다. |
| `404` | `PRODUCT_NOT_FOUND` | 상품이 없거나, 고객 조회·새 주문·변경에서 삭제된 상품이다. | 상품을 찾을 수 없습니다. |
| `404` | `ORDER_NOT_FOUND` | 주문이 없거나 다른 고객의 주문이다. | 주문을 찾을 수 없습니다. |
| `404` | `USER_NOT_FOUND` | 경로의 사용자가 요청자가 아니다. | 사용자를 찾을 수 없습니다. |
| `409` | `DUPLICATE_BRAND_NAME` | 삭제되지 않은 브랜드 중에 같은 이름이 있다. | 이미 사용 중인 브랜드 이름입니다. |
| `409` | `DUPLICATE_PRODUCT_NAME` | 같은 브랜드의 삭제되지 않은 상품 중에 같은 이름이 있다. | 이 브랜드에 같은 이름의 상품이 있습니다. |
| `409` | `BRAND_HAS_PRODUCTS` | 삭제되지 않은 상품이 연결된 브랜드를 삭제하려 했다. | 연결된 상품이 있어 브랜드를 삭제할 수 없습니다. |
| `409` | `ORDER_ALREADY_CONFIRMED` | 이미 확정된 주문을 확정하려 했다. | 이미 확정된 주문입니다. |
| `409` | `BRAND_CHANGE_NOT_ALLOWED` | 상품 수정에서 브랜드를 바꾸려 했다. | 상품의 브랜드는 바꿀 수 없습니다. |
| `409` | `PRODUCT_NOT_AVAILABLE` | 확정하려는 주문의 상품이 삭제되었다. | 주문한 상품 중 판매하지 않는 상품이 있습니다. |
| `409` | `INSUFFICIENT_STOCK` | 확정하려는 주문의 수량보다 재고가 적다. | 재고가 부족합니다. |
| `409` | `INSUFFICIENT_POINT` | 확정하려는 주문의 금액보다 잔액이 적다. | 포인트 잔액이 부족합니다. |
| `409` | `POINT_BALANCE_LIMIT_EXCEEDED` | 충전 후 잔액이 표현할 수 있는 범위를 넘는다. | 충전할 수 있는 한도를 넘었습니다. |

상태 코드는 누가 거절하는지로 나눈다. 요청 형식이 틀리면 interfaces가 `400`, 요청자를 식별하지 못하면 `401`, 관리자 접근 필터가 막으면 `403`, 대상이 없으면 `404`, 형식은 맞지만 저장된 상태를 보고 도메인이 업무 규칙으로 거절하면 `409`이다.
