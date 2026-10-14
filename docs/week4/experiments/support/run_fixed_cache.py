"""Fixed-data cache trial with configured short warmup and one or more measured runs."""
import argparse
import json
import re
from pathlib import Path
from remote_runner import RemoteRunner
from run import save

def counters(runner):
    response=runner.ssh(runner.base['appHost'],'curl --fail --silent http://127.0.0.1:8081/actuator/prometheus')
    if response['exitCode']:raise RuntimeError('Cache metrics missing')
    return {k:float(v) for k,v in re.findall(
        r'^experiment_latest_cache_total\{[^\n]*result="([^"]+)"[^\n]*\}\s+([0-9.eE+-]+)',response['stdout'],re.M)}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--ssh-key',required=True)
    parser.add_argument('--repeats',type=int,default=1)
    args=parser.parse_args()
    if args.repeats < 1:parser.error('--repeats must be positive')
    runner=RemoteRunner(args.config,args.ssh_key);root=runner.experiment
    runs=json.loads((root/'all-runs.json').read_text()) if (root/'all-runs.json').exists() else []
    comparisons=json.loads((root/'comparison-runs.json').read_text()) if (root/'comparison-runs.json').exists() else []
    key=runner.base['cache']['key']
    if not re.fullmatch(r'[a-zA-Z0-9:_.=+-]+',key):raise ValueError('Unsafe cache key')
    steps=[(rate,runner.base['warmupSeconds'],'warmup') for rate in runner.base['warmupSteps']]
    steps.extend([(1700,runner.base['durationSeconds'],'measure')]*args.repeats)
    for rate,seconds,phase in steps:
        if not runner.api_check()['ok']:raise RuntimeError('Pre-check failed')
        refreshed=runner.ssh(runner.base['dbHost'],'sudo docker exec week4-db-redis-master-1 redis-cli EXPIRE '+key+' 600')
        if refreshed['exitCode'] or refreshed['stdout'].strip()!='1':raise RuntimeError('Cache not filled')
        before=counters(runner);print('STARTING',phase,rate,flush=True)
        result=runner.run(rate,seconds,phase,1,defer_aggregation=True)
        after=counters(runner);delta={k:after.get(k,0)-before.get(k,0) for k in set(before)|set(after)}
        runs.append(result['runId']);save(root/'all-runs.json',runs)
        save(root/'results'/result['runId']/'cache-metrics.json',{'before':before,'after':after,'delta':delta,
            'scope':'Includes preflight and recovery API checks'})
        if phase=='measure':
            comparisons.append(result['runId']);save(root/'comparison-runs.json',comparisons)
        if result['exitCode']!=0 or not result['recovery']['ok'] or not all(result['collection'].values()):
            raise RuntimeError('Capture/recovery failed')
        if delta.get('hit',0)<=0 or any(delta.get(k,0) for k in ('miss','fill','read_error','write_error')):
            raise RuntimeError('Not a hit-only trial')

if __name__=='__main__':main()
