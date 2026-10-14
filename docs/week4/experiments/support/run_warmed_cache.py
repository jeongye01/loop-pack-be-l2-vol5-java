"""Staged fixed-data cache trial. Collect now; aggregate only after all load has stopped."""
import argparse
import json
import re
import statistics
from datetime import datetime
from pathlib import Path
from run import Runner, save


def metric(text, name):
    return sum(float(v) for v in re.findall(r'^'+name+r'(?:\{[^\n]*\})?\s+([0-9.eE+-]+)', text, re.M))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--ssh-key', required=True)
    args=parser.parse_args()
    config=json.loads(args.config.read_text())
    if config.get('loadHost'):
        from remote_runner import RemoteRunner
        runner=RemoteRunner(args.config,args.ssh_key)
    else:
        runner=Runner(args.config,args.ssh_key)
    root=runner.experiment
    settings=runner.base['stabilization']
    key=runner.base['cache']['key']
    if not re.fullmatch(r'[a-zA-Z0-9:_.=+-]+',key):
        raise ValueError('Unsafe cache key')
    runs=[]; measures=[]; warm=[]

    def counters():
        response=runner.ssh(runner.base['appHost'],
            'curl --fail --silent http://127.0.0.1:8081/actuator/prometheus')
        if response['exitCode']!=0:raise RuntimeError('Cache counters unavailable')
        return {k:float(v) for k,v in re.findall(
            r'^experiment_latest_cache_total\{[^\n]*result="([^"]+)"[^\n]*\}\s+([0-9.eE+-]+)',response['stdout'],re.M)}

    def execute(rate,phase,repeat):
        if not runner.api_check()['ok']:raise RuntimeError('Response pre-check failed')
        refreshed=runner.ssh(runner.base['dbHost'],
            'sudo docker exec week4-db-redis-master-1 redis-cli EXPIRE '+key+' 600')
        if refreshed['exitCode']!=0 or refreshed['stdout'].strip()!='1':raise RuntimeError('Cache key missing')
        before=counters()
        result=runner.run(rate,settings['seconds'] if phase=='warmup' else runner.base['durationSeconds'],
                          phase,repeat,defer_aggregation=True)
        after=counters();delta={k:after.get(k,0)-before.get(k,0) for k in set(before)|set(after)}
        out=root/'results'/result['runId'];runs.append(result['runId']);save(root/'all-runs.json',runs)
        save(out/'cache-metrics.json',{'before':before,'after':after,'delta':delta,'ttlRefreshedBeforeRunSeconds':600,
                                    'scope':'Includes preflight and recovery API checks'})
        if phase=='measure':
            measures.append(result['runId']);save(root/'comparison-runs.json',measures)
        if result['exitCode']!=0 or not result['recovery']['ok'] or not all(result['collection'].values()):
            raise RuntimeError('Capture, response verification or recovery failed')
        if delta.get('hit',0)<=0 or any(delta.get(k,0) for k in ('miss','fill','read_error','write_error')):
            raise RuntimeError('Cache was not hit-only')
        if phase=='warmup':
            summary=json.loads((out/'summary.json').read_text())['metrics']
            samples=json.loads((out/'resources.json').read_text())
            elapsed=(datetime.fromisoformat(samples[-1]['at'])-datetime.fromisoformat(samples[0]['at'])).total_seconds()
            jit=(metric(samples[-1]['app']['stdout'],'jvm_compilation_time_ms_total')-
                 metric(samples[0]['app']['stdout'],'jvm_compilation_time_ms_total'))/elapsed
            value={'runId':result['runId'],'rps':rate,'p95Ms':summary['client_latency_ms']['values']['p(95)'],
                'appCpuMeanPercent':statistics.mean(metric(x['app']['stdout'],'process_cpu_usage')*100 for x in samples),
                'jitMsPerSecond':jit,'dropped':summary.get('dropped_iterations',{}).get('values',{}).get('count',0),
                'errorRate':summary['http_req_failed']['values']['rate']}
            warm.append(value);save(root/'warmup-observations.json',warm);print(json.dumps(value),flush=True)
            if value['dropped'] or value['errorRate']:raise RuntimeError('Warmup did not sustain its requested load')
            return value
        print(json.dumps({'measurementCollected':repeat,'runId':result['runId']}),flush=True)

    try:
        for i,rate in enumerate(settings['steps'],1):execute(rate,'warmup',i)
        previous=None;stable=False
        for i in range(1,settings['maximumRounds']+1):
            current=execute(settings['rps'],'warmup',len(settings['steps'])+i)
            if previous:
                decision={'previous':previous['runId'],'current':current['runId'],
                    'p95RelativeChange':abs(current['p95Ms']-previous['p95Ms'])/max(previous['p95Ms'],1),
                    'cpuPercentagePointChange':abs(current['appCpuMeanPercent']-previous['appCpuMeanPercent']),
                    'jitMsPerSecond':[previous['jitMsPerSecond'],current['jitMsPerSecond']]}
                stable=(decision['p95RelativeChange']<=settings['maxP95RelativeChange'] and
                        decision['cpuPercentagePointChange']<=settings['maxCpuPercentagePointChange'] and
                        max(decision['jitMsPerSecond'])<=settings['maxJitMsPerSecond'])
                decision['stable']=stable;save(root/'stabilization.json',decision)
                if stable:break
            previous=current
        if not stable:raise RuntimeError('No stabilization within configured bound')
        for i in range(1,4):execute(1700,'measure',i)
        save(root/'execution.json',{'status':'collected','measurementRuns':measures,'note':'Full aggregation pending'})
    except Exception as error:
        save(root/'execution.json',{'status':'stopped','reason':str(error),'measurementRuns':measures})
        raise

if __name__=='__main__':main()
