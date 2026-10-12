"""Read-only, short RPS discovery against the existing local perf application."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
from collections import Counter
from datetime import datetime, timezone

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
DATA = HERE.parent / 'seed-data/uniform-10k-v1'
URL = 'http://127.0.0.1:18080/api/v1/products?brandId=7&sort=price_asc&page=0&size=20'


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def command(args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=20)
    return {'exitCode': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr}


def rows(name):
    with (DATA / name).open() as stream:
        yield from csv.DictReader(stream)


def baseline():
    with urllib.request.urlopen(urllib.request.Request(URL, headers={'X-USER-ID': '1'}), timeout=15) as res:
        body = json.load(res)
    products = sorted((p for p in rows('products.csv') if p['brand_id'] == '7'),
                      key=lambda p: (int(p['price']), int(p['id'])))
    brands = {int(b['id']): b['name'] for b in rows('brands.csv')}
    stocks = {int(s['product_id']): int(s['quantity']) for s in rows('stocks.csv')}
    likes = Counter(int(l['product_id']) for l in rows('likes.csv') if not l['deleted_at'])
    expected = {'content': [{'id': int(p['id']), 'name': p['name'], 'price': int(p['price']),
                            'brand': {'id': 7, 'name': brands[7]}, 'likeCount': likes[int(p['id'])],
                            'soldOut': stocks[int(p['id'])] == 0} for p in products[:20]],
                'page': 0, 'size': 20, 'totalElements': len(products)}
    if body.get('meta', {}).get('result') != 'SUCCESS' or body.get('data') != expected:
        raise RuntimeError('Baseline response differs from CSV')
    return body


def scrape(path):
    try:
        with urllib.request.urlopen('http://127.0.0.1:18081/actuator/prometheus', timeout=3) as res:
            path.write_bytes(res.read())
    except Exception as error:
        path.write_text(str(error))


def run(out, label, rps, seconds, vus):
    folder = out / label
    folder.mkdir()
    env = dict(os.environ, RPS=str(rps), DURATION=str(seconds), VUS=str(vus),
               EXPECTED_FILE=str(out / 'expected.json'), SUMMARY_FILE=str(folder / 'summary.json'))
    scrape(folder / 'metrics-before.txt')
    started = datetime.now(timezone.utc).isoformat()
    start = time.monotonic()
    samples = []
    with (folder / 'k6.log').open('w') as log:
        process = subprocess.Popen(['k6', 'run', '--quiet', str(HERE / 'products.js')],
                                   env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            while process.poll() is None:
                samples.append({'elapsed': time.monotonic() - start,
                                'processes': command(['ps', '-axo', 'pid,pcpu,pmem,rss,comm']),
                                'docker': command(['docker', 'stats', '--no-stream', '--format',
                                                   '{{json .}}', 'week4-perf-mysql-1'])})
                if time.monotonic() - start > seconds + 45:
                    raise TimeoutError('k6 exceeded bounded run time')
                time.sleep(2)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            save(folder / 'resources.json', samples)
    scrape(folder / 'metrics-after.txt')
    metrics = json.loads((folder / 'summary.json').read_text())['metrics']
    def value(metric, key, default=0):
        return metrics.get(metric, {}).get('values', {}).get(key, default)
    result = {'label': label, 'startedAt': started, 'targetRps': rps, 'durationSeconds': seconds,
              'vus': vus, 'exitCode': process.returncode,
              'completed': value('http_reqs', 'count'), 'rpsIncludingDrain': value('http_reqs', 'rate'),
              'dropped': value('dropped_iterations', 'count'),
              'failureRate': value('http_req_failed', 'rate'),
              'invalidResponseRate': value('invalid_response', 'rate'),
              'p50ms': value('http_req_duration', 'p(50)'), 'p95ms': value('http_req_duration', 'p(95)'),
              'p99ms': value('http_req_duration', 'p(99)')}
    save(folder / 'result.json', result)
    print(json.dumps(result), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--rates', default='1,2,3,5')
    parser.add_argument('--duration', type=int, default=20)
    parser.add_argument('--warmup', type=int, default=30)
    parser.add_argument('--vus', type=int, default=200)
    args = parser.parse_args()
    rates = [int(x) for x in args.rates.split(',')]
    if min(rates + [args.duration, args.vus]) <= 0 or args.warmup < 0:
        raise ValueError('Invalid load parameters')
    out = HERE / 'results' / datetime.now().strftime('%Y%m%d-%H%M%S')
    out.mkdir(parents=True)
    save(out / 'expected.json', baseline())
    save(out / 'environment.json', {
        'url': URL, 'args': vars(args), 'git': command(['git', 'rev-parse', 'HEAD']),
        'gitStatus': command(['git', 'status', '--short']),
        'os': command(['sw_vers']), 'hardware': command(['sysctl', '-n', 'hw.model', 'hw.ncpu', 'hw.memsize']),
        'java': command(['java', '-version']), 'k6': command(['k6', 'version']),
        'docker': command(['docker', 'info', '--format', 'cpus={{.NCPU}} memory={{.MemTotal}}']),
        'datasetManifestSha256': hashlib.sha256((DATA / 'manifest.json').read_bytes()).hexdigest(),
        'scriptsSha256': {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                          for name in ('probe.py', 'products.js')},
        'app': {'profile': 'local,perf', 'ddlAuto': 'none', 'hikariMax': 40, 'hikariMin': 30,
                'connectionTimeoutMs': 3000, 'tomcatMaxThreads': 200},
    })
    print(f'Results: {out}', flush=True)
    if args.warmup:
        run(out, 'warmup', min(rates[0], 10), args.warmup, args.vus)
    results = []
    for index, rate in enumerate(rates):
        result = run(out, f'{index + 1:02d}-{rate}rps', rate, args.duration, args.vus)
        results.append(result)
        save(out / 'results.json', results)
        if (result['exitCode'] or result['failureRate'] > .01 or result['invalidResponseRate'] > .01
                or result['dropped'] > rate * args.duration * .01):
            print('Stop increasing load: errors or dropped iterations exceeded 1%. This is an exploratory stop rule, not an SLO.', flush=True)
            break
        time.sleep(5)


if __name__ == '__main__':
    main()
