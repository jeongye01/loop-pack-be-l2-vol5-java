"""Read recorded traces/resources; no load generation. Argument: experiment folder."""
import json,re,math,statistics,sys
from pathlib import Path
from collections import defaultdict,Counter
from datetime import datetime
root=Path(sys.argv[1])
def dist(v):
 if not v:return None
 v=sorted(v);return {'mean':round(statistics.mean(v),3),'p50':round(v[math.ceil(len(v)*.5)-1],3),'p95':round(v[math.ceil(len(v)*.95)-1],3),'p99':round(v[math.ceil(len(v)*.99)-1],3),'max':round(v[-1],3)}
def metric(s,k):return sum(float(v) for v in re.findall(r'^'+k+r'(?:\{[^\n]*\})?\s+([0-9.eE+-]+)',s,re.M))
output=[]
for rid in json.loads((root/'comparison-runs.json').read_text()):
 p=root/'results'/rid;bins=defaultdict(lambda:defaultdict(list));allv=defaultdict(list);outcomes=defaultdict(Counter);stages=defaultdict(list);start=None;errors=Counter()
 with (p/'traces.jsonl').open() as f:
  for l in f:
   t=json.loads(l)
   if start is None:start=t['startedAtMs']
   b=int((t['startedAtMs']-start)//10000)*10
   outcomes[b][t['outcome']]+=1
   bins[b]['latency'].append(t['latencyMs'])
   for key,v in t['timings'].items():allv[key].append(v)
   sv=t.get('server');tim=t['timings']
   allv['clockMinusHttp'].append(t['latencyMs']-tim['blocked']-tim['duration'])
   if sv:
    bins[b]['server'].append(sv['serverDurationMs'])
    for name,x in sv['stages'].items():stages[name].append(x['selfMs'])
   if t['outcome']=='success' and sv:
    bins[b]['gap'].append(t['latencyMs']-sv['serverDurationMs'])
    allv['successClient'].append(t['latencyMs']);allv['successServer'].append(sv['serverDurationMs'])
    allv['successGap'].append(t['latencyMs']-sv['serverDurationMs'])
    allv['successWaitingMinusServer'].append(tim['waiting']-sv['serverDurationMs'])
   if t['outcome']!='success':errors[t.get('error')]+=1
 samples=json.loads((p/'resources.json').read_text());timeline=[]
 for i,s in enumerate(samples):
  a=s['app']['stdout'];local=[]
  for line in s['local']['stdout'].splitlines():
   z=line.split()
   if len(z)>=5 and z[4].split('/')[-1] in ['k6','python3','Python']:local.append({'process':z[4],'cpu':float(z[1]),'rssMiB':round(int(z[3])/1024,1)})
  row={'t':round(datetime.fromisoformat(s['at']).timestamp()-start/1000,1),'jitMs':metric(a,'jvm_compilation_time_ms_total'),'cpuSeconds':metric(a,'process_cpu_time_ns_total')/1e9,'gcSeconds':metric(a,'jvm_gc_pause_seconds_sum'),'gcMaxMs':metric(a,'jvm_gc_pause_seconds_max')*1000,'uptime':metric(a,'process_uptime_seconds'),'active':metric(a,'experiment_active_requests'),'local':local}
  if i:
   prev=timeline[-1];row['jitDeltaMs']=round(row['jitMs']-prev['jitMs'],1)
  timeline.append(row)
 first,last=timeline[0],timeline[-1]
 data={'runId':rid,'timings':{k:dist(v) for k,v in allv.items()},'bins':[{'startSecond':k,'outcomes':outcomes[k],**{kk:dist(vv) for kk,vv in v.items()}} for k,v in sorted(bins.items())],'jvm':{'sampleSeconds':last['t']-first['t'],'jitDeltaMs':last['jitMs']-first['jitMs'],'gcDeltaSeconds':last['gcSeconds']-first['gcSeconds'],'cpuDeltaSeconds':last['cpuSeconds']-first['cpuSeconds'],'uptimeStart':first['uptime']},'timeline':timeline,'stages':{k:dist(v) for k,v in stages.items()},'errors':dict(errors)}
 output.append(data)
 print(json.dumps({k:data[k] for k in ['runId','timings','jvm','bins']},ensure_ascii=False))
(root/'variance-analysis.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
