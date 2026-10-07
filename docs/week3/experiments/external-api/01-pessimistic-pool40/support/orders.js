import http from 'k6/http';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

const orders = new SharedArray('orders', () => JSON.parse(open(__ENV.DATA_FILE)).orders);
export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS),
      timeUnit: '1s',
      duration: `${__ENV.DURATION}s`,
      preAllocatedVUs: Number(__ENV.VUS),
      maxVUs: Number(__ENV.VUS),
      gracefulStop: '15s',
    },
  },
  maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'max', 'p(50)', 'p(95)', 'p(99)'],
};

export default function () {
  const index = exec.scenario.iterationInTest;
  if (index >= orders.length) return;
  const order = orders[index];
  const requestId = `${__ENV.RUN_ID}-${index}`;
  const start = Date.now();
  const payment = http.post(`${__ENV.FAKE_PAYMENT_URL}/payments/authorize`, JSON.stringify({
    orderId: order.orderId, idempotencyKey: requestId, delayMs: 300,
  }), { headers: { 'Content-Type': 'application/json' }, timeout: '10s' });
  if (payment.status !== 200) {
    const end = Date.now();
    console.log(JSON.stringify({
      runId: __ENV.RUN_ID, requestId, operation: 'payment.authorize', orderId: order.orderId,
      buyerId: order.buyerId, startNs: `${start}000000`, endNs: `${end}000000`, latencyMs: end - start,
      httpStatus: payment.status || null, outcome: 'technicalError', errorCode: 'PAYMENT_AUTHORIZE_FAILED',
      technicalKind: 'paymentAuthorize', transportError: payment.error || null, compensation: 'not-needed',
    }));
    return;
  }
  const response = http.post(`${__ENV.BASE_URL}/api/v1/orders/${order.orderId}/confirm`, null, {
    headers: { 'X-USER-ID': String(order.buyerId), 'X-Lab-Request-Id': requestId },
    timeout: '10s',
    tags: { name: 'order.confirm' },
  });
  const end = Date.now();
  let body = null;
  try { body = response.json(); } catch (_) { /* Preserve malformed responses as technical errors. */ }
  const errorCode = body && body.meta ? (body.meta.errorCode || null) : null;
  const outcome = response.status === 200 && body && body.meta.result === 'SUCCESS' ? 'success'
    : response.status >= 400 && response.status < 500 && errorCode === 'INSUFFICIENT_STOCK' ? 'businessRejection'
    : 'technicalError';
  let compensation = 'not-needed';
  if (outcome !== 'success') {
    const cancelled = http.post(`${__ENV.FAKE_PAYMENT_URL}/payments/cancel`, JSON.stringify({
      orderId: order.orderId, idempotencyKey: requestId,
    }), { headers: { 'Content-Type': 'application/json' }, timeout: '10s' });
    compensation = cancelled.status === 200 ? 'cancelled' : 'cancel-failed';
  }
  console.log(JSON.stringify({
    runId: __ENV.RUN_ID, requestId, operation: 'order.confirm',
    orderId: order.orderId, buyerId: order.buyerId,
    startNs: `${start}000000`, endNs: `${end}000000`, latencyMs: end - start,
    httpStatus: response.status || null, outcome, errorCode,
    transportError: response.error || null, paymentAuthorizeMs: payment.timings.duration,
    compensation,
  }));
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2) };
}
