"""Isolated local HTTP experiment. Only commerce_experiment fixtures are reset."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import random
import re
import subprocess
import time
import urllib.request

LAB = Path(__file__).resolve().parent.parent
ROOT = next(p for p in LAB.parents if (p / 'gradlew').exists())
RAW = ROOT / 'build/serializable-experiment'
CONTAINER = 'commerce-serializable-mysql'
DATABASE = 'commerce_experiment'
BASE = 'http://127.0.0.1:18080'
PRICE = 4000
BALANCE = 100000
SEED = 20261005


def now():
    return datetime.now(timezone.utc).isoformat()


def command(args, **kwargs):
    return subprocess.check_output(args, cwd=ROOT, text=True, **kwargs).strip()


def sql(statement):
    return command(['docker', 'exec', '-i', '-e', 'MYSQL_PWD=lab-root', CONTAINER,
                    'mysql', '-uroot', '--batch', '--raw', '--skip-column-names', DATABASE], input=statement)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def capture(args):
    try:
        return {'command': args, 'output': command(args, stderr=subprocess.STDOUT), 'status': 'collected'}
    except (subprocess.CalledProcessError, FileNotFoundError) as error:
        return {'command': args, 'status': 'unavailable', 'reason': str(error)}


def snapshot():
    return {'capturedAt': now(),
            'host': capture(['sysctl', '-n', 'hw.model', 'hw.ncpu', 'hw.memsize']),
            'os': capture(['sw_vers']), 'java': capture(['java', '-version']),
            'cpu': capture(['top', '-l', '1', '-n', '0']),
            'memory': capture(['vm_stat']), 'swap': capture(['sysctl', 'vm.swapusage']),
            'processes': capture(['ps', '-axo', 'pid,pcpu,pmem,rss,comm']),
            'dockerResources': capture(['docker', 'info', '--format', '{{json .NCPU}} {{json .MemTotal}}']),
            'dbResources': capture(['docker', 'inspect', CONTAINER, '--format', 'CPU={{.HostConfig.NanoCpus}} Memory={{.HostConfig.Memory}} Swap={{.HostConfig.MemorySwap}}']),
            'dbImage': capture(['docker', 'inspect', CONTAINER, '--format', '{{.Image}}']),
            'dockerStats': capture(['docker', 'stats', '--no-stream', '--format', '{{json .}}', CONTAINER]),
            'mysql': sql('SELECT VERSION(), @@global.transaction_isolation, @@innodb_buffer_pool_size;'),
            'k6': capture(['k6', 'version']), 'timeSeries': None}


def fixture(scenario, count, two_share):
    rng = random.Random(SEED)
    sizes = [2] * round(count * two_share) + [3] * (count - round(count * two_share))
    rng.shuffle(sizes)
    orders = []
    for index in range(count):
        products = [1] if scenario == 'S1' else ([1] + rng.sample(list(range(2, 11)), sizes[index] - 1)
                     if scenario == 'S2' else rng.sample(list(range(1, 11)), sizes[index]))
        orders.append({'orderId': index + 1, 'buyerId': index + 1, 'products': products,
                       'amount': PRICE * len(products)})
    # Performance runs keep inventory above planned demand so stockout responses
    # do not hide the lock contention being measured.
    stock = {str(i): max(count + 400, 1000) for i in range(1, 11)}
    data = {'scenario': scenario, 'seed': SEED, 'twoItemShare': two_share if scenario != 'S1' else None,
            'price': PRICE, 'initialBalance': BALANCE, 'initialStock': stock, 'orders': orders}
    # Explicit lab database and table list: never use the existing Swagger DB.
    statements = ['SET FOREIGN_KEY_CHECKS=0;', 'TRUNCATE TABLE order_items;', 'TRUNCATE TABLE orders;',
                  'TRUNCATE TABLE stock;', 'TRUNCATE TABLE point;', 'TRUNCATE TABLE product;', 'TRUNCATE TABLE brand;', 'TRUNCATE TABLE users;',
                  'SET FOREIGN_KEY_CHECKS=1;', 'START TRANSACTION;',
                  "INSERT INTO brand(id,name,created_at,updated_at) VALUES(1,'lab',NOW(6),NOW(6));"]
    statements.append('INSERT INTO product(id,brand_id,name,price,created_at,updated_at) VALUES ' +
                      ','.join(f"({i},1,'{chr(64+i)}',{PRICE},NOW(6),NOW(6))" for i in range(1, 11)) + ';')
    statements.append('INSERT INTO stock(product_id,quantity,created_at,updated_at) VALUES ' +
                      ','.join(f"({i},{stock[str(i)]},NOW(6),NOW(6))" for i in range(1, 11)) + ';')
    for start in range(0, count, 500):
        batch = orders[start:start+500]
        statements.append('INSERT INTO users(id,created_at,updated_at) VALUES ' +
                          ','.join(f"({x['buyerId']},NOW(6),NOW(6))" for x in batch) + ';')
        statements.append('INSERT INTO point(user_id,balance,created_at,updated_at) VALUES ' +
                          ','.join(f"({x['buyerId']},{BALANCE},NOW(6),NOW(6))" for x in batch) + ';')
        statements.append('INSERT INTO orders(id,buyer_id,status,created_at,updated_at) VALUES ' +
                          ','.join(f"({x['orderId']},{x['buyerId']},0,NOW(6),NOW(6))" for x in batch) + ';')
        statements.append('INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price) VALUES ' +
                          ','.join(f"({x['orderId']},{i},'{chr(64+i)}',1,{PRICE})" for x in batch for i in x['products']) + ';')
    statements.append('COMMIT;')
    sql('\n'.join(statements))
    return data


def effective_isolation():
    # These are live application sessions, not the separate inspection connection.
    rows = sql("SELECT v.VARIABLE_VALUE,COUNT(*) FROM performance_schema.variables_by_thread v "
               "JOIN performance_schema.threads t ON t.THREAD_ID=v.THREAD_ID "
               "WHERE t.PROCESSLIST_USER='application' AND v.VARIABLE_NAME='transaction_isolation' "
               "GROUP BY v.VARIABLE_VALUE;")
    if not rows or any(line.split('\t')[0] != 'REPEATABLE-READ' for line in rows.splitlines()):
        raise RuntimeError(f'Unexpected application session isolation: {rows!r}')
    return rows


def validate(data, traces):
    products = {str(row[0]): row[1] for row in json.loads(sql("SELECT JSON_ARRAYAGG(JSON_ARRAY(product_id,quantity)) FROM stock;"))}
    orders = {row[0]: row for row in json.loads(sql(
        'SELECT JSON_ARRAYAGG(JSON_ARRAY(o.id,o.status,o.amount,o.paid_at,p.balance)) '
        'FROM orders o JOIN point p ON p.user_id=o.buyer_id;'))}
    items = json.loads(sql('SELECT JSON_ARRAYAGG(JSON_ARRAY(order_id,product_id,quantity,unit_price,product_name)) FROM order_items;'))
    expected_items = sorted([[x['orderId'], i, 1, PRICE, chr(64+i)] for x in data['orders'] for i in x['products']])
    by_order = {trace['orderId']: trace for trace in traces}
    failures = []
    deductions = Counter()
    confirmed = 0
    for original in data['orders']:
        oid = original['orderId']
        row = orders.get(oid)
        trace = by_order.get(oid)
        success = trace is not None and trace['outcome'] == 'success'
        if success:
            deductions.update(original['products'])
        expected_status = 1 if success else 0
        expected_amount = original['amount'] if success else None
        expected_balance = BALANCE - original['amount'] if success else BALANCE
        if row is None or row[1] != expected_status or row[2] != expected_amount or row[4] != expected_balance or ((row[3] is not None) != success):
            failures.append({'check': 'orderPaymentBalance', 'orderId': oid,
                             'expected': [expected_status, expected_amount, success, expected_balance], 'actual': row})
        if row and row[1] == 1:
            confirmed += 1
    for pid, initial in data['initialStock'].items():
        expected = initial - deductions[int(pid)]
        if products.get(pid) != expected or products.get(pid, -1) < 0:
            failures.append({'check': 'stock', 'productId': pid, 'expected': expected, 'actual': products.get(pid)})
    if sorted(items) != expected_items:
        failures.append({'check': 'orderItemsUnchanged', 'expectedCount': len(expected_items), 'actualCount': len(items)})
    if len(orders) != len(data['orders']):
        failures.append({'check': 'orderCount', 'expected': len(data['orders']), 'actual': len(orders)})
    if len(by_order) != len(traces):
        failures.append({'check': 'uniqueOrderRequests', 'expected': len(traces), 'actual': len(by_order)})
    return {'status': 'passed' if not failures else 'failed', 'checks': ['orderPaymentBalance', 'stock', 'orderItemsUnchanged', 'orderCount', 'uniqueOrderRequests'],
            'failures': failures, 'confirmedOrders': confirmed, 'finalStock': products}


def percentile(values, percent):
    if not values:
        return None
    values = sorted(values)
    pos = (len(values)-1)*percent/100
    lo, hi = math.floor(pos), math.ceil(pos)
    return values[lo] + (values[hi]-values[lo])*(pos-lo)


def latency(rows):
    values = [x['latencyMs'] for x in rows]
    return {'count': len(rows), **{f'p{p}': percentile(values, p) for p in (50, 95, 99)}}


def correlate_errors(traces, app_log):
    # Tomcat access log supplies requestId + full thread name + request time interval.
    access = RAW / 'access.log'
    records = {}
    if access.exists():
        for line in access.read_text().splitlines():
            parts = line.split('|')
            if len(parts) == 5 and parts[0] != '-':
                try:
                    records[parts[0]] = (parts[1], int(parts[3]), int(parts[4]))
                except ValueError:
                    pass
    messages = []
    for line in app_log.splitlines():
        match = re.match(r'^([^|]+)\|([^|]+)\|(WARN|ERROR)\|[^|]+\|(.*)', line)
        if match:
            messages.append((int(datetime.fromisoformat(match[1]).timestamp()*1000), match[2], match[4]))
    for trace in traces:
        if trace['outcome'] != 'technicalError':
            continue
        record = records.get(trace['requestId'])
        evidence = [] if not record else [message for stamp, thread, message in messages
                                         if thread == record[0] and record[1] <= stamp <= record[2]]
        trace['serverEvidence'] = evidence
        joined = '\n'.join(evidence).lower()
        trace['technicalKind'] = ('deadlock' if 'deadlock found' in joined else
                                  'lockTimeout' if 'lock wait timeout' in joined else
                                  'transport' if trace['transportError'] else 'unclassified')


def run(scenario, rps, duration, repetition, vus, two_share, label):
    count = rps * duration
    run_id = f'{label}-{scenario}-{rps}rps-{repetition:02}'
    folder = LAB / 'results' / run_id
    if folder.exists():
        raise RuntimeError(f'Refusing to overwrite {folder}')
    folder.mkdir(parents=True)
    raw = RAW / run_id
    raw.mkdir(parents=True, exist_ok=True)
    data = fixture(scenario, count, two_share)
    data_file = raw / 'fixture.json'
    write_json(data_file, data)
    sessions = effective_isolation()
    sha = command(['git', 'rev-parse', 'HEAD'])
    env_snapshot = {'runId': run_id, 'gitSha': sha, 'start': snapshot(), 'end': None}
    write_json(folder / 'environment.json', env_snapshot)
    env = os.environ.copy()
    env.update({'DATA_FILE': str(data_file), 'RUN_ID': run_id, 'RPS': str(rps), 'DURATION': str(duration),
                'VUS': str(vus), 'BASE_URL': BASE, 'SUMMARY_FILE': str(raw / 'summary.json')})
    start_log = (RAW / 'app.log').stat().st_size
    begin = now()
    log_path = raw / 'traces.jsonl'
    try:
        # k6 console output appends when the path already exists; each Run must start clean.
        log_path.unlink(missing_ok=True)
        (raw / 'summary.json').unlink(missing_ok=True)
        with (raw / 'k6.log').open('w') as output:
            completed = subprocess.run(['k6', 'run', '--log-format', 'raw', '--console-output', str(log_path),
                                        str(LAB / 'support/orders.js')], cwd=ROOT, env=env,
                                       stdout=output, stderr=subprocess.STDOUT, timeout=duration+60)
        traces = [json.loads(line) for line in log_path.read_text().splitlines() if line.startswith('{')]
        # Access logs are unbuffered, and all HTTP responses have returned at this point.
        with (RAW / 'app.log').open() as log:
            log.seek(start_log)
            evidence = log.read()
        (raw / 'server.log').write_text(evidence)
        correlate_errors(traces, evidence)
        write_json(folder / 'traces.json', {'runId': run_id, 'timestampResolution': '1ms', 'traces': traces})
        summary = json.loads((raw / 'summary.json').read_text())
        dropped = summary.get('metrics', {}).get('dropped_iterations', {}).get('values', {}).get('count', 0)
        started = summary.get('metrics', {}).get('http_reqs', {}).get('values', {}).get('count', len(traces))
        finished = now()
        counts = Counter(x['outcome'] for x in traces)
        seconds = (max(int(x['endNs']) for x in traces)-min(int(x['startNs']) for x in traces))/1e9 if traces else 0
        errors = Counter(x.get('technicalKind', x['errorCode']) for x in traces if x['outcome'] != 'success')
        by_result = {key: latency([x for x in traces if x['outcome'] == key]) for key in ('success', 'businessRejection', 'technicalError')}
        out_of_stock = [int(x['endNs']) for x in traces if x['errorCode'] == 'INSUFFICIENT_STOCK']
        split = min(out_of_stock) if out_of_stock else None
        phases = None if split is None else {
            'boundaryNs': str(split), 'boundaryMeaning': 'first observed insufficient-stock response (not exact DB depletion time)',
            'before': latency([x for x in traces if int(x['endNs']) < split]),
            'after': latency([x for x in traces if int(x['endNs']) >= split])}
        result = {'schemaVersion': 1, 'experimentId': 'stock-contention/17-pessimistic-stock-sufficient', 'runId': run_id,
                  'gitSha': sha, 'workingTreeChanges': True,
                  'status': 'completed' if completed.returncode == 0 and started == len(traces) else 'incomplete',
                  'startedAt': begin, 'finishedAt': finished,
                  'config': {'scenario': scenario, 'rps': rps, 'durationSeconds': duration, 'repetition': repetition,
                             'vus': vus, 'initialStock': data['initialStock'], 'twoItemShare': data['twoItemShare'],
                             'randomSeed': SEED, 'dataSha256': hashlib.sha256(data_file.read_bytes()).hexdigest(),
                             'applicationSessionIsolation': sessions, 'jvmOptions': [], 'specId': 'local-01', 'retryCount': 0},
                  'requests': {'planned': count, 'started': started, 'completed': len(traces), 'notStarted': dropped,
                               'incomplete': max(0, started-len(traces)),
                               **{key: counts[key] for key in ('success', 'businessRejection', 'technicalError')},
                               'optimisticConflict': 0, 'errors': dict(errors)},
                  'metrics': {'measurementSeconds': seconds, 'throughput': len(traces)/seconds if seconds else None,
                              'successThroughput': counts['success']/seconds if seconds else None,
                              'latencyMs': latency(traces), 'latencyByOutcomeMs': by_result, 'stockoutPhases': phases,
                              'optimisticConflictRate': None, 'optimisticConflictRateReason': 'not used'},
                  'validation': validate(data, traces),
                  'artifacts': {'traces': 'traces.json', 'environment': 'environment.json',
                                'raw': os.path.relpath(raw, folder)}, 'k6ExitCode': completed.returncode}
        write_json(folder / 'result.json', result)
        print(f"{run_id}: {dict(counts)} DB={result['validation']['status']} dropped={dropped}", flush=True)
        return result
    except Exception as error:
        write_json(folder / 'result.json', {'runId': run_id, 'status': 'aborted', 'error': str(error), 'startedAt': begin})
        raise
    finally:
        env_snapshot['end'] = snapshot()
        write_json(folder / 'environment.json', env_snapshot)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--scenario', choices=['S1', 'S2', 'S3'], required=True)
    parser.add_argument('--rps', type=int, default=10)
    parser.add_argument('--duration', type=int, default=60)
    parser.add_argument('--repetition', type=int, default=1)
    parser.add_argument('--vus', type=int, default=200)
    parser.add_argument('--two-item-share', type=float)
    parser.add_argument('--label', default='pilot')
    args = parser.parse_args()
    if args.scenario != 'S1' and args.two_item_share is None:
        parser.error('S2/S3 require an agreed --two-item-share')
    run(args.scenario, args.rps, args.duration, args.repetition, args.vus,
        args.two_item_share if args.two_item_share is not None else 0.5, args.label)


if __name__ == '__main__':
    main()
