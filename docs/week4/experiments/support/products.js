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
      outcome = body.meta?.result === 'SUCCESS' && JSON.stringify(canonical(body.data)) === expectedData
        ? 'success' : 'mismatch';
      if (outcome === 'mismatch') difference = {expected: expected.data, actual: body};
    } catch (_) { outcome = 'mismatch'; difference = {error:'invalid JSON', body:response.body}; }
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
