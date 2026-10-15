"""Single combined API SLO. Reuse RemoteRunner, products.js, seed validation and resource capture."""
import argparse
import json
import shlex
from pathlib import Path
from remote_runner import RemoteRunner
from run import save


def cache_state(runner):
    ids = ','.join(str(p['id']) for p in sorted(runner.expected['data']['content'], key=lambda p: p['id']))
    patterns = ['products::v3:products?sort=latest&brand=all&page=0&size=20:rev=*',
                'like-counts::v3:products/like-counts?ids='+ids+':rev=*',
                'stocks::v3:products/stocks?ids='+ids+':rev=*']
    if runner.base.get('cache', {}).get('writeInvalidate', False):
        patterns = [p.replace('v3:', 'v4:').removesuffix(':rev=*') for p in patterns]
    state = {'ttlMs': {}}
    for pattern in patterns:
        found = runner.ssh(runner.base['dbHost'], 'sudo docker exec week4-db-redis-master-1 redis-cli --scan --pattern '+shlex.quote(pattern))
        if found['exitCode']: raise RuntimeError('Cache scan failed')
        keys = found['stdout'].splitlines()
        # Writes create many revision keys; avoid one SSH round trip per key.
        script = "local r={} for i,k in ipairs(KEYS) do r[i]=redis.call('PTTL',k) end return r"
        for start in range(0, len(keys), 100):
            batch = keys[start:start + 100]
            ttl = runner.ssh(runner.base['dbHost'],
                'sudo docker exec week4-db-redis-master-1 redis-cli --raw EVAL '
                + shlex.quote(script) + ' ' + str(len(batch)) + ' ' + ' '.join(map(shlex.quote, batch)))
            values = ttl['stdout'].splitlines()
            if ttl['exitCode'] or len(values) != len(batch): raise RuntimeError('Cache TTL read failed')
            state['ttlMs'].update(zip(batch, map(int, values)))
    info = runner.ssh(runner.base['dbHost'], 'sudo docker exec week4-db-redis-master-1 redis-cli INFO stats')
    if info['exitCode']: raise RuntimeError('Redis stats unavailable')
    state['redisStats'] = {k: int(v) for line in info['stdout'].splitlines() if ':' in line
                           for k, v in [line.split(':', 1)] if k in ('keyspace_hits', 'keyspace_misses', 'expired_keys', 'evicted_keys', 'total_commands_processed')}
    commands = runner.ssh(runner.base['dbHost'], 'sudo docker exec week4-db-redis-master-1 redis-cli INFO commandstats')
    if commands['exitCode']: raise RuntimeError('Redis command stats unavailable')
    state['commandCalls'] = {name.removeprefix('cmdstat_'): int(fields.split(',')[0].split('=')[1])
                             for line in commands['stdout'].splitlines() if line.startswith('cmdstat_')
                             for name, fields in [line.split(':', 1)]}
    return state


def main():
    p = argparse.ArgumentParser(); p.add_argument('--config', type=Path, required=True); p.add_argument('--ssh-key', required=True); phases = p.add_mutually_exclusive_group(); phases.add_argument('--measure-only', action='store_true'); phases.add_argument('--warmup-only', action='store_true'); p.add_argument('--continue-after-warmup-failure', action='store_true')
    a = p.parse_args(); r = RemoteRunner(a.config, a.ssh_key)
    p99_limit_ms = r.base.get('slo', {}).get('p99Ms', 100)
    purchase_limit_ms = r.base.get('slo', {}).get('purchaseP99Ms', p99_limit_ms)
    like_limit_ms = r.base.get('slo', {}).get('likeP99Ms', p99_limit_ms)
    history = r.experiment/'slo-runs.json'
    runs = json.loads(history.read_text()) if history.exists() else []
    schedule = [(1700,60,'measure')] if a.measure_only else [(rate,r.base.get('warmupSeconds',20),'warmup') for rate in r.base.get('warmupSteps',[300,700])] + [(1700,60,'measure')]
    if a.warmup_only:
        schedule = [step for step in schedule if step[2] == 'warmup']
    for rate, seconds, phase in schedule:
        if not r.api_check()['ok']: raise RuntimeError('Seed response comparison failed')
        before = cache_state(r)
        print(f'START {phase}: {rate} RPS for {seconds}s', flush=True)
        result = r.run(rate, seconds, phase, defer_aggregation=True)
        out = r.experiment/'results'/result['runId']
        save(out/'cache-state.json', {'before': before, 'after': cache_state(r)})
        m = json.loads((out/'summary.json').read_text())['metrics']
        def value(name, field='count'): return m.get(name, {}).get('values', {}).get(field, 0)
        summary = {'runId': result['runId'], 'p99LimitMs': p99_limit_ms, 'phase': phase, 'rps': rate, 'seconds': seconds,
                   'planned': rate*seconds, 'started': value('request_started'), 'completed': value('request_completed'),
                   'dropped': value('dropped_iterations'), 'successRate': value('request_success','rate'),
                   'failed': value('request_success','fails'), 'timeouts': value('request_timeouts'), 'transportErrors': value('request_transport_errors'), 'httpErrors': value('request_http_errors'),
                   'mismatches': value('response_mismatch'), 'httpRequests': value('http_reqs'),
                   'httpRps': value('http_reqs','rate'), 'httpFailureRate': value('http_req_failed','rate'),
                   'latencyMs': m.get('client_latency_ms',{}).get('values',{}),
                   'successLatencyMs': m.get('client_success_latency_ms',{}).get('values',{}),
                   'recovered': result['recovery']['ok'], 'collection': result['collection'], 'exitCode': result['exitCode']}
        if phase == 'measure' and r.base.get('purchase', {}).get('rps', 0):
            summary['purchase'] = {'planned': r.base['purchase']['rps'] * seconds,
                'started': value('purchase_started'), 'completed': value('purchase_completed'),
                'successRate': value('purchase_success', 'rate'), 'failed': value('purchase_success', 'fails'),
                'timeouts': value('purchase_timeouts'), 'httpErrors': value('purchase_http_errors'),
                'invalidResponses': value('purchase_invalid_response'),
                'latencyMs': m.get('purchase_latency_ms', {}).get('values', {}),
                'createLatencyMs': m.get('purchase_create_latency_ms', {}).get('values', {}),
                'confirmLatencyMs': m.get('purchase_confirm_latency_ms', {}).get('values', {})}
        summary['complete'] = (result['status']=='collected' and summary['exitCode']==0 and summary['recovered']
            and all(summary['collection'].values()) and summary['started']==summary['completed'] and summary['started']>=summary['planned'])
        summary['sloPassed'] = (summary['complete'] and summary['successRate']==1 and summary['dropped']==0
            and summary['mismatches']==0 and summary['latencyMs'].get('p(99)',float('inf'))<=p99_limit_ms)
        summary['readPassed'] = summary['sloPassed']
        if 'purchase' in summary:
            purchase = summary['purchase']
            summary['purchase']['p99LimitMs'] = purchase_limit_ms
            summary['purchasePassed'] = (purchase['started'] >= purchase['planned']
                and purchase['started'] == purchase['completed'] and purchase['successRate'] == 1
                and purchase['invalidResponses'] == 0 and purchase['latencyMs'].get('p(99)', float('inf')) <= purchase_limit_ms)
            summary['sloPassed'] = summary['sloPassed'] and summary['purchasePassed']
        if phase == 'measure' and r.base.get('likes', {}).get('pairRps', 0):
            summary['likes'] = {}
            for step in ('register', 'cancel'):
                prefix = 'like_' + step
                item = {'planned': r.base['likes']['pairRps'] * seconds,
                    'started': value(prefix + '_started'), 'completed': value(prefix + '_completed'),
                    'successRate': value(prefix + '_success', 'rate'),
                    'failed': value(prefix + '_success', 'fails'),
                    'latencyMs': m.get(prefix + '_latency_ms', {}).get('values', {})}
                item['passed'] = (item['started'] >= item['planned'] and item['started'] == item['completed']
                    and item['successRate'] == 1 and (like_limit_ms is None or item['latencyMs'].get('p(99)', float('inf')) <= like_limit_ms))
                item['p99LimitMs'] = like_limit_ms
                summary['likes'][step] = item
            summary['likesPassed'] = all(v['passed'] for v in summary['likes'].values())
            summary['sloPassed'] = summary['sloPassed'] and summary['likesPassed']
        save(out/'slo-result.json',summary); runs.append(summary); save(r.experiment/'slo-runs.json',runs)
        print(json.dumps(summary),flush=True)
        if phase=='warmup' and a.continue_after_warmup_failure and result['status']=='collected' and summary['recovered'] and all(summary['collection'].values()) and summary['exitCode']==0:
            continue
        if not summary['complete'] or summary['successRate']!=1 or summary['dropped']:
            raise RuntimeError('Stop escalation after failed load or recovery; artifacts preserved')

if __name__ == '__main__': main()
