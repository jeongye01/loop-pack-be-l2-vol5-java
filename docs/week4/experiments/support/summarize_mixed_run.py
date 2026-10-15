"""Mixed-load evidence from all client requests and sampled server resources."""
import json
import re
import statistics
import sys
from collections import Counter
from pathlib import Path

root=Path(sys.argv[1]); folder=sorted((root/'results').glob('*-measure-*'))[-1]
result=json.loads((folder/'result.json').read_text()); samples=json.loads((folder/'resources.json').read_text())
def dist(values):
    a=sorted(values)
    return {'mean':statistics.mean(a),'max':max(a),'p99':a[min(len(a)-1,int(len(a)*.99))]} if a else None
traces=[]
for line in (folder/'server.log').read_text().splitlines():
    if 'EXPERIMENT_JSON ' in line:
        try:
            trace=json.loads(line.split('EXPERIMENT_JSON ',1)[1])
            if trace['runId']==result['runId']: traces.append(trace)
        except ValueError: pass
write_traces=[t for t in traces if t['path'].startswith('/api/v1/orders')]
traces=[t for t in traces if t['path']=='/api/v1/products']
metrics={}
for name in ['hikaricp_connections_active','hikaricp_connections_pending','hikaricp_connections_max','tomcat_threads_busy_threads']:
    metrics[name]=dist([float(v) for s in samples for v in re.findall(r'^'+name+r'(?:\{[^\n]*\})?\s+([0-9.eE+-]+)',s['app']['stdout'],re.M)])
cpu={}
for host in ['app','db']:
    by={}
    for s in samples:
        for line in s[host]['stdout'].splitlines():
            if line.startswith('{"'):
                v=json.loads(line);by.setdefault(v['Name'],[]).append(float(v['CPUPerc'].rstrip('%')))
    cpu[host]={k:dist(v) for k,v in by.items()}
pre=json.loads((folder/'preflight.json').read_text())['samples'][-1]['app']['stdout']
post=result['recovery']['samples'][-1]['app']['stdout']
def cache_counters(text):
    return {line.rsplit(' ',1)[0]:float(line.rsplit(' ',1)[1]) for line in text.splitlines() if line.startswith('catalog_cache') and not line.startswith('catalog_cache_redis_duration')}
old=cache_counters(pre); new=cache_counters(post)
evidence={'runId':result['runId'],'resources':metrics,'dockerCpuPercent':cpu,
    'loadK6CpuPercent':dist([float(line.split()[1]) for s in samples for line in s['local']['stdout'].splitlines() if len(line.split())>=5 and line.split()[4].split('/')[-1]=='k6']),
    'writeTraceCount':len(write_traces),
    'writeServerDurationMs':dist([t['serverDurationMs'] for t in write_traces]),
    'writeConnectionWaitMs':dist([t['connectionWaitMs'] for t in write_traces]),
    'sampledReadTraceCount':len(traces),'serverDurationMs':dist([t['serverDurationMs'] for t in traces]),
    'connectionWaitMs':dist([t['connectionWaitMs'] for t in traces]),
    'sqlCountHistogram':dict(Counter(len(t['queries']) for t in traces)),
    'revisionRechecks':sum(sum('select namespace, revision' in q['sql'] for q in t['queries'])>1 for t in traces),
    'stockOriginTraceCount':sum('ProductUseCase.findStockStatuses' in t['stages'] for t in traces),
    'originalListTraceCount':sum('ProductUseCase.findBasicPage' in t['stages'] for t in traces),
    'cacheCounterDelta':{k:v-old.get(k,0) for k,v in new.items()},
    'notes':['Read/write server traces are separated by path; read sampling and write instrumentation depend on deployed image.',
        'Resource snapshots every 5s; maxima do not represent continuous occupancy.',
        'Cache deltas include preflight/recovery checks and other traffic; misses can include repeated probes.']}
(root/'mixed-observations.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps(evidence,indent=2))
