import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter, Rate } from 'k6/metrics';

const config = JSON.parse(open(__ENV.CONFIG_FILE));
const expected = JSON.parse(open(__ENV.EXPECTED_FILE));
const latency = new Trend('client_latency_ms', true);
const successLatency = new Trend('client_success_latency_ms', true);
const started = new Counter('request_started');
const completed = new Counter('request_completed');
const success = new Rate('request_success');
const mismatches = new Counter('response_mismatch');
const timeouts = new Counter('request_timeouts');
const transportErrors = new Counter('request_transport_errors');
const httpErrors = new Counter('request_http_errors');
const purchaseStarted = new Counter('purchase_started');
const purchaseCompleted = new Counter('purchase_completed');
const purchaseSuccess = new Rate('purchase_success');
const purchaseLatency = new Trend('purchase_latency_ms', true);
const createLatency = new Trend('purchase_create_latency_ms', true);
const confirmLatency = new Trend('purchase_confirm_latency_ms', true);
const purchaseHttpErrors = new Counter('purchase_http_errors');
const purchaseTimeouts = new Counter('purchase_timeouts');
const purchaseInvalid = new Counter('purchase_invalid_response');
const purchaseEnabled = config.phase === 'measure' && config.purchase?.rps > 0;
const likeMetrics = Object.fromEntries(['register', 'cancel'].map(step => [step, {
  started: new Counter(`like_${step}_started`), completed: new Counter(`like_${step}_completed`),
  success: new Rate(`like_${step}_success`), latency: new Trend(`like_${step}_latency_ms`, true),
}]));
// Optional short-window metrics for expiry experiments; normal workloads remain unchanged.
const windowSeconds = config.latencyWindowSeconds || 0;
const windows = windowSeconds > 0 ? Array.from({length: Math.ceil(config.durationSeconds / windowSeconds)}, (_, i) => ({
  latency: new Trend(`client_window_${i}_latency_ms`, true),
  success: new Rate(`client_window_${i}_success`),
})) : [];
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(k => [k, canonical(value[k])]));
  }
  return value;
}
const expectedData = JSON.stringify(canonical(expected.data));
export const options = {
  scenarios: { products: {
    executor: 'constant-arrival-rate', rate: Math.round(config.rps * 100), timeUnit: '100s',
    duration: `${config.durationSeconds}s`, preAllocatedVUs: config.vus, maxVUs: config.vus,
    gracefulStop: `${config.gracefulStopSeconds}s`,
  } },
  maxRedirects: 0, summaryTrendStats: ['avg', 'min', 'max', 'p(50)', 'p(95)', 'p(99)'],
};
if (purchaseEnabled) {
  options.scenarios.purchases = {
    executor: 'constant-arrival-rate', exec: 'purchase', rate: config.purchase.rps, timeUnit: '1s',
    duration: `${config.durationSeconds}s`, preAllocatedVUs: config.purchase.vus, maxVUs: config.purchase.vus,
    gracefulStop: `${config.gracefulStopSeconds}s`,
  };
}
if (config.phase === 'measure' && config.likes?.pairRps > 0) {
  options.scenarios.likes = {
    executor: 'constant-arrival-rate', exec: 'likePair', rate: config.likes.pairRps, timeUnit: '1s',
    duration: `${config.durationSeconds}s`, preAllocatedVUs: config.likes.vus, maxVUs: config.likes.vus,
    gracefulStop: `${config.gracefulStopSeconds}s`,
  };
}
function emit(event) { if (config.emitTraces !== false) console.log('TRACE_JSON ' + JSON.stringify(event)); }
export default function () {
  const requestId = `${config.runId}-${exec.scenario.iterationInTest}`;
  emit({event:'start', runId:config.runId, requestId, at:Date.now()});
  started.add(1);
  const startedAtMs = Date.now();
  const response = http.get(config.url, {
    headers: {'X-USER-ID': String(config.userId), 'X-Run-ID': config.runId, 'X-Request-ID': requestId},
    timeout: `${config.timeoutSeconds}s`, tags: {name:'product.list'},
  });
  const finishedAtMs = Date.now();
  const latencyMs = finishedAtMs - startedAtMs;
  let outcome, difference = null;
  if (response.status === 0) outcome = response.error_code === 1050 ? 'timeout' : 'transport_error';
  else if (response.status !== 200) outcome = 'http_error';
  else {
    try {
      const body = response.json();
      outcome = body.meta?.result === 'SUCCESS' && (config.validateSeedResponse === false || JSON.stringify(canonical(body.data)) === expectedData)
        ? 'success' : 'mismatch';
      if (outcome === 'mismatch') difference = {expected: expected.data, actual: body};
    } catch (_) { outcome = 'mismatch'; difference = {error:'invalid JSON', body:response.body}; }
  }
  if (windows.length) {
    const index = Math.floor((startedAtMs - exec.scenario.startTime) / (1000 * windowSeconds));
    if (index >= 0 && index < windows.length) {
      windows[index].latency.add(latencyMs);
      windows[index].success.add(outcome === 'success');
    }
  }
  latency.add(latencyMs, {outcome});
  completed.add(1, {outcome});
  success.add(outcome === 'success');
  if (outcome === 'success') successLatency.add(latencyMs);
  if (outcome === 'mismatch') mismatches.add(1);
  if (outcome === 'timeout') timeouts.add(1);
  if (outcome === 'transport_error') transportErrors.add(1);
  if (outcome === 'http_error') httpErrors.add(1);
  emit({event:'end', runId:config.runId, requestId, operation:'product.list',
    input:config.input, startedAtMs, finishedAtMs, latencyMs, httpStatus:response.status || null,
    outcome, errorCode:response.error_code || null, error:response.error || null,
    timings:response.timings, difference});
  if (outcome === 'mismatch') exec.test.abort('Product response differs from expected CSV');
}
export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2) };
}

// Real two-step purchase, with no retries for an ambiguous write response.
export function purchase() {
  const index = exec.scenario.iterationInTest;
  const buyer = config.purchase.buyers[index];
  if (!buyer) exec.test.abort('Dedicated buyer pool exhausted');
  const product = config.purchase.products[index % config.purchase.products.length];
  const base = config.url.split('/api/')[0];
  const start = Date.now();
  purchaseStarted.add(1);
  let orderId = null, outcome = 'http_error', status = null, code = null;
  const params = (step) => ({headers: {'X-USER-ID': String(buyer), 'Content-Type': 'application/json',
    'X-Run-ID': config.runId, 'X-Request-ID': `${config.runId}-purchase-${index}-${step}`},
    timeout: `${config.timeoutSeconds}s`, tags: {name:`purchase.${step}`}});
  const responseBody = (response, expectedStatus) => {
    status = response.status;
    if (status !== expectedStatus) {
      if (response.error_code === 1050) { purchaseTimeouts.add(1); outcome = 'timeout'; }
      else { purchaseHttpErrors.add(1); outcome = 'http_error'; }
      code = response.error_code || response.body;
      return null;
    }
    try {
      const body = response.json();
      if (body.meta?.result !== 'SUCCESS') throw new Error('non-success body');
      return body.data;
    } catch (_) { purchaseInvalid.add(1); outcome = 'invalid_response'; return null; }
  };
  const validOrder = (order, expectedStatus) => order && Number.isSafeInteger(order.id) && order.status === expectedStatus
    && order.items?.length === 1 && order.items[0].productId === product.id && order.items[0].quantity === 1
    && order.items[0].unitPrice === product.price && order.totalAmount === product.price;
  try {
    let stepStart = Date.now();
    const created = responseBody(http.post(`${base}/api/v1/orders`, JSON.stringify({items:[{productId:product.id,quantity:1}]}), params('create')), 201);
    createLatency.add(Date.now() - stepStart);
    if (!created) return;
    orderId = created.id;
    if (!validOrder(created, 'DRAFT')) { purchaseInvalid.add(1); outcome = 'invalid_response'; return; }
    stepStart = Date.now();
    const confirmed = responseBody(http.post(`${base}/api/v1/orders/${orderId}/confirm`, null, params('confirm')), 200);
    confirmLatency.add(Date.now() - stepStart);
    if (!confirmed) return;
    if (!validOrder(confirmed, 'CONFIRMED') || confirmed.id !== orderId || confirmed.payment?.amount !== product.price) {
      purchaseInvalid.add(1); outcome = 'invalid_response'; return;
    }
    outcome = 'success';
  } finally {
    const elapsed = Date.now() - start;
    purchaseCompleted.add(1);
    purchaseSuccess.add(outcome === 'success');
    purchaseLatency.add(elapsed, {outcome});
    console.log('PURCHASE_JSON ' + JSON.stringify({runId:config.runId,index,buyer,productId:product.id,
      expectedAmount:product.price,orderId,outcome,httpStatus:status,error:code,latencyMs:elapsed}));
  }
}

// Each pair uses a fresh user/product relation; no retries after ambiguous writes.
export function likePair() {
  const index = exec.scenario.iterationInTest;
  const user = config.purchase.buyers[index];
  if (!user) exec.test.abort('Dedicated like user pool exhausted');
  const productId = config.purchase.products[index % config.purchase.products.length].id;
  const url = `${config.url.split('/api/')[0]}/api/v1/products/${productId}/likes`;
  for (const step of ['register', 'cancel']) {
    const m = likeMetrics[step]; m.started.add(1);
    const start = Date.now();
    const params = {headers: {'X-USER-ID': String(user), 'X-Run-ID': config.runId,
      'X-Request-ID': `${config.runId}-like-${index}-${step}`},
      timeout: `${config.timeoutSeconds}s`, tags: {name:`like.${step}`}};
    const response = step === 'register' ? http.post(url, null, params) : http.del(url, null, params);
    let ok = false;
    try { ok = response.status === (step === 'register' ? 201 : 200)
      && response.json().meta?.result === 'SUCCESS'; } catch (_) { /* recorded below */ }
    const elapsed = Date.now() - start;
    m.completed.add(1); m.success.add(ok); m.latency.add(elapsed);
    console.log('LIKE_JSON ' + JSON.stringify({runId:config.runId,index,user,productId,step,
      outcome:ok ? 'success' : 'failure',httpStatus:response.status,errorCode:response.error_code || null,latencyMs:elapsed}));
  }
}
