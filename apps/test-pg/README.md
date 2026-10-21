# Local Test PG

`test-pg` is a separate WireMock-based HTTP app for the commerce payment exercise. It keeps synthetic transactions in process memory and does not connect to a real PG or store card data.

Run it with Docker Compose from the repository root:

```bash
docker compose -f docker/pg-compose.yml up --build -d
```

The local base URL is `http://127.0.0.1:18089`. The contract API requires `X-USER-ID: 135135`:

- `POST /api/v1/payments` creates a `PENDING` transaction.
- `GET /api/v1/payments/{transactionKey}` returns one stored transaction.
- `GET /api/v1/payments?orderId={providerOrderId}` returns the transactions for an order.

For local scenario control, `POST /__test/payments/status` accepts `transactionKey` and `status` (`SUCCESS` or `FAILED`) and changes a pending transaction. A terminal status cannot be changed. This control endpoint is separate from the PG contract.

Transaction state lives in memory and is cleared when the `test-pg` process restarts. Commerce application restarts do not clear it while this separate process remains running.
