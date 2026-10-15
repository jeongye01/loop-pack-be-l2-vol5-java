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
    state = {'ttlMs': {}}
    for pattern in patterns:
        found = runner.ssh(runner.base['dbHost'], 'sudo docker exec week4-db-redis-master-1 redis-cli --scan --pattern '+shlex.quote(pattern))
        if found['exitCode']: raise RuntimeError('Cache scan failed')
        for key in found['stdout'].splitlines():
            ttl = runner.ssh(runner.base['dbHost'], 'sudo docker exec week4-db-redis-master-1 redis-cli PTTL '+shlex.quote(key))
            if ttl['exitCode']: raise RuntimeError('Cache TTL read failed')
            state['ttlMs'][key] = int(ttl['stdout'].strip())
    info = runner.ssh(runner.base['dbHost'], 'sudo docker exec week4-db-redis-master-1 redis-cli INFO stats')
    if info['exitCode']: raise RuntimeError('Redis stats unavailable')
    state['redisStats'] = {k: int(v) for line in info['stdout'].splitlines() if ':' in line
                           for k, v in [line.split(':', 1)] if k in ('keyspace_hits', 'keyspace_misses', 'expired_keys', 'evicted_keys', 'total_commands_processed')}
    return state


def main():
    p = argparse.ArgumentParser(); p.add_argument('--config', type=Path, required=True); p.add_argument('--ssh-key', required=True); p.add_argument('--measure-only', action='store_true'); p.add_argument('--continue-after-warmup-failure', action='store_true')
    a = p.parse_args(); r = RemoteRunner(a.config, a.ssh_key)
    history = r.experiment/'slo-runs.json'
    runs = json.loads(history.read_text()) if history.exists() else []
    schedule = [(1700,60,'measure')] if a.measure_only else [(rate,r.base.get('warmupSeconds',20),'warmup') for rate in r.base.get('warmupSteps',[300,700])] + [(1700,60,'measure')]
    for rate, seconds, phase in schedule:
        if not r.api_check()['ok']: raise RuntimeError('Seed response comparison failed')
        before = cache_state(r)
        print(f'START {phase}: {rate} RPS for {seconds}s', flush=True)
        result = r.run(rate, seconds, phase, defer_aggregation=True)
        out = r.experiment/'results'/result['runId']
        save(out/'cache-state.json', {'before': before, 'after': cache_state(r)})
        m = json.loads((out/'summary.json').read_text())['metrics']
        def value(name, field='count'): return m.get(name, {}).get('values', {}).get(field, 0)
        summary = {'runId': result['runId'], 'phase': phase, 'rps': rate, 'seconds': seconds,
                   'planned': rate*seconds, 'started': value('request_started'), 'completed': value('request_completed'),
                   'dropped': value('dropped_iterations'), 'successRate': value('request_success','rate'),
                   'failed': value('request_success','fails'), 'timeouts': value('request_timeouts'), 'transportErrors': value('request_transport_errors'), 'httpErrors': value('request_http_errors'),
                   'mismatches': value('response_mismatch'), 'httpRequests': value('http_reqs'),
                   'httpRps': value('http_reqs','rate'), 'httpFailureRate': value('http_req_failed','rate'),
                   'latencyMs': m.get('client_latency_ms',{}).get('values',{}),
                   'successLatencyMs': m.get('client_success_latency_ms',{}).get('values',{}),
                   'recovered': result['recovery']['ok'], 'collection': result['collection'], 'exitCode': result['exitCode']}
        summary['complete'] = (result['status']=='collected' and summary['exitCode']==0 and summary['recovered']
            and all(summary['collection'].values()) and summary['started']==summary['completed'] and summary['started']>=summary['planned'])
        summary['sloPassed'] = (summary['complete'] and summary['successRate']==1 and summary['dropped']==0
            and summary['mismatches']==0 and summary['latencyMs'].get('p(99)',float('inf'))<=200)
        save(out/'slo-result.json',summary); runs.append(summary); save(r.experiment/'slo-runs.json',runs)
        print(json.dumps(summary),flush=True)
        if phase=='warmup' and a.continue_after_warmup_failure and result['status']=='collected' and summary['recovered'] and all(summary['collection'].values()) and summary['exitCode']==0:
            continue
        if not summary['complete'] or summary['successRate']!=1 or summary['dropped']:
            raise RuntimeError('Stop escalation after failed load or recovery; artifacts preserved')

if __name__ == '__main__': main()
