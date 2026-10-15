import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter, Rate } from 'k6/metrics';
const c = JSON.parse(open(__ENV.CONFIG_FILE));
const expected = JSON.parse(open(__ENV.EXPECTED_FILE));
const duration = new Trend('screen_duration_ms', true);
const successes = new Trend('screen_success_duration_ms', true);
const success = new Rate('screen_success');
const started = new Counter('screen_started');
const completed = new Counter('screen_completed');
const invalid = new Counter('screen_mismatch');
function canonical(v) {
  if (Array.isArray(v)) return v.map(canonical);
  if (v !== null && typeof v === 'object') return Object.fromEntries(Object.keys(v).sort().map(k => [k, canonical(v[k])]));
  return v;
}
const wanted = Object.fromEntries(['data', 'counts', 'stocks'].map(k => [k, JSON.stringify(canonical(expected[k]))]));
export const options = {
  scenarios: { screen: { executor: 'constant-arrival-rate', rate: c.rps, timeUnit: '1s',
    duration: `${c.durationSeconds}s`, preAllocatedVUs: c.vus, maxVUs: c.vus, gracefulStop: `${c.gracefulStopSeconds}s` } },
  thresholds: {
    'http_req_duration{name:product.list}': ['p(99)>=0'],
    'http_req_duration{name:product.counts}': ['p(99)>=0'],
    'http_req_duration{name:product.stocks}': ['p(99)>=0'],
  },
  batchPerHost: 2, maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'max', 'p(50)', 'p(95)', 'p(99)'],
};
function params(name, id) { return { timeout: `${c.timeoutSeconds}s`, tags: {name},
  headers: {'X-USER-ID':String(c.userId),'X-Run-ID':c.runId,'X-Request-ID':id} }; }
function valid(response, name) {
  if (response.status !== 200) return false;
  try {
    const body = response.json();
    const ok = body.meta?.result === 'SUCCESS' && JSON.stringify(canonical(body.data)) === wanted[name];
    if (!ok) { invalid.add(1); exec.test.abort(`Response mismatch: ${name}`); }
    return ok;
  } catch (_) { invalid.add(1); exec.test.abort(`Invalid response: ${name}`); return false; }
}
export default function () {
  const id = `${c.runId}-${exec.scenario.iterationInTest}`;
  started.add(1);
  const begin = Date.now();
  const list = http.get(c.url, params('product.list', id+'-list'));
  let ok = false;
  if (valid(list, 'data')) {
    const ids = list.json().data.content.map(p => p.id).sort((a,b)=>a-b).join(',');
    const base = c.url.split('/api/v1/')[0];
    const replies = http.batch([
      {method:'GET',url:base+'/api/v1/products/like-counts?ids='+ids,params:params('product.counts',id+'-counts')},
      {method:'GET',url:base+'/api/v1/products/stocks?ids='+ids,params:params('product.stocks',id+'-stocks')},
    ]);
    const ended = Date.now();
    const countsOk = valid(replies[0], 'counts');
    const stocksOk = valid(replies[1], 'stocks');
    ok = countsOk && stocksOk;
    duration.add(ended-begin);
    if(ok) successes.add(ended-begin);
  } else duration.add(Date.now()-begin);
  success.add(ok); completed.add(1);
}
export function handleSummary(data) { return {[__ENV.SUMMARY_FILE]:JSON.stringify(data,null,2)}; }
