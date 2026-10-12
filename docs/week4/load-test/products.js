import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  }
  return value;
}
const expected = JSON.stringify(canonical(JSON.parse(open(__ENV.EXPECTED_FILE)).data));
const invalid = new Rate('invalid_response');
export const options = {
  scenarios: {
    products: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 10),
      timeUnit: '1s',
      duration: `${__ENV.DURATION || 20}s`,
      preAllocatedVUs: Number(__ENV.VUS || 200),
      maxVUs: Number(__ENV.VUS || 200),
      gracefulStop: '10s',
    },
  },
  maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'max', 'p(50)', 'p(95)', 'p(99)'],
};

export default function () {
  const response = http.get(
    `${__ENV.BASE_URL || 'http://127.0.0.1:18080'}/api/v1/products?brandId=7&sort=price_asc&page=0&size=20`,
    { headers: { 'X-USER-ID': '1' }, timeout: '5s', tags: { name: 'products.brand7.price_asc' } },
  );
  let correct = false;
  try {
    const body = response.json();
    correct = response.status === 200 && body.meta.result === 'SUCCESS'
      && JSON.stringify(canonical(body.data)) === expected;
  } catch (_) { /* Invalid JSON is counted as an invalid response. */ }
  invalid.add(!correct);
  check(response, { 'same product page': () => correct });
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2) };
}
