"""Summarize completed single-API SLO runs, including sampled traces and resource evidence."""
from pathlib import Path
import json,re,statistics,sys
from tomcat_metrics import summarize
root=Path(sys.argv[1])
def values(s,k):return [float(v) for v in re.findall(r'^'+re.escape(k)+r'(?:\{[^\n]*\})?\s+([0-9.eE+-]+)',s,re.M)]
def metric(s,k):return sum(values(s,k))
def dist(a):
 a=sorted(a)
 return {'min':min(a),'max':max(a),'mean':statistics.mean(a),'p99':a[min(len(a)-1,int(len(a)*.99))]} if a else None
for row in json.loads((root/'slo-runs.json').read_text()):
 out=root/'results'/row['runId'];r=json.loads((out/'result.json').read_text());samples=json.loads((out/'resources.json').read_text());pre=json.loads((out/'preflight.json').read_text())['samples'][-1];post=r['recovery']['samples'][-1]
 o={'runId':row['runId'],'sampleCount':len(samples),'tomcat':summarize(samples),'metrics':{},'dockerCpuPercent':{},'note':'Sampled resource maxima; preflight-to-recovery counter deltas include validation and recovery requests.'}
 for k in ['hikaricp_connections_active','hikaricp_connections_pending','experiment_active_requests','experiment_log_queue_size']:
  o['metrics'][k]=dist([metric(s['app']['stdout'],k) for s in samples])
 cachelines=[l for l in post['app']['stdout'].splitlines() if l.startswith('catalog_cache')]
 o['cacheCountersBefore']=[l for l in pre['app']['stdout'].splitlines() if l.startswith('catalog_cache')];o['cacheCountersAfter']=cachelines
 for host in ['app','db']:
  by={}
  for sample in samples:
   for line in sample[host]['stdout'].splitlines():
    if line.startswith('{"'):
     v=json.loads(line);by.setdefault(v['Name'],[]).append(float(v['CPUPerc'].rstrip('%')))
  o['dockerCpuPercent'][host]={k:dist(v) for k,v in by.items()}
 o['loadK6CpuPercent']=dist([float(l.split()[1]) for s in samples for l in s['local']['stdout'].splitlines() if len(l.split())>=5 and l.split()[4].split('/')[-1]=='k6'])
 traces=[]
 for line in (out/'server.log').read_text().splitlines():
  if 'EXPERIMENT_JSON ' in line:
   try: t=json.loads(line.split('EXPERIMENT_JSON ',1)[1]);traces.append(t)
   except ValueError:pass
 o['sampledTraces']={'count':len(traces),'serverDurationMs':dist([t['serverDurationMs'] for t in traces]),'connectionWaitMs':dist([t['connectionWaitMs'] for t in traces]),'sqlCount':dist([len(t['queries']) for t in traces]),'sqlErrors':sum(not q['success'] for t in traces for q in t['queries'])}
 (out/'resource-observations.json').write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps(o,ensure_ascii=False))
