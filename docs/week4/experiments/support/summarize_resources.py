"""Summarize recorded resources for run IDs in an experiment/comparison-runs.json."""
import json,re,statistics,math,sys
from pathlib import Path
from datetime import datetime
from tomcat_metrics import summarize as summarize_tomcat
ROOT=Path(sys.argv[1])
def dist(v):
 v=sorted(v)
 return {'mean':statistics.mean(v),'max':max(v),'p95':v[math.ceil(len(v)*.95)-1]} if v else None
def metric(s,key):
 return sum(float(m) for m in re.findall(r'^'+key+r'(?:\{[^\n]*\})?\s+([0-9.eE+-]+)',s,re.M))
def memory_mib(value):
 m=re.fullmatch(r'([0-9.]+)(B|KiB|MiB|GiB|TiB)',value.split('/')[0].strip())
 if not m: raise ValueError('Unknown Docker memory unit: '+value)
 return float(m[1])*{'B':1/1048576,'KiB':1/1024,'MiB':1,'GiB':1024,'TiB':1048576}[m[2]]
def cpu(s,key='cpu'):
 return list(map(int,re.search(r'^'+key+r'\s+(.+)$',s,re.M)[1].split()))[:8]
def disk(s):
 return list(map(int,re.search(r'^\s*\d+\s+\d+\s+nvme0n1\s+(.+)$',s,re.M)[1].split()))
selected=json.loads((ROOT/'comparison-runs.json').read_text())
for p in [ROOT/'results'/rid/'result.json' for rid in selected]:
 source=p.parent/'analysis.json' if (p.parent/'analysis.json').exists() else p
 r=json.loads(source.read_text())
 if r['config']['phase']!='measure' or r['status']=='running':continue
 d=p.parent;s=json.loads((d/'resources.json').read_text());t=[json.loads(l) for l in (d/'traces.jsonl').read_text().splitlines()]
 result={'runId':r['runId'],'sort':r['config']['input']['sort'],'sampleCount':len(s),'resultSource':source.name,'hosts':{}}
 for h,container in [('app','week4-commerce-api-1'),('db','week4-db-mysql-1')]:
  stats=[next(json.loads(l) for l in x[h]['stdout'].splitlines() if l.startswith('{"') and json.loads(l)['Name']==container) for x in s]
  cpus=[float(x['CPUPerc'].rstrip('%')) for x in stats]
  host_cpu=[];wait=[];percore=[[],[]];io=[]
  for a,b in zip(s,s[1:]):
   dt=(datetime.fromisoformat(b['at'])-datetime.fromisoformat(a['at'])).total_seconds()
   av,bv=cpu(a[h]['stdout']),cpu(b[h]['stdout']);delta=[y-x for x,y in zip(av,bv)];total=sum(delta)
   host_cpu.append(100*(total-delta[3]-delta[4]-delta[7])/total);wait.append(100*delta[4]/total)
   for i in range(2):
    v=[y-x for x,y in zip(cpu(a[h]['stdout'],'cpu'+str(i)),cpu(b[h]['stdout'],'cpu'+str(i)))]; z=sum(v);percore[i].append(100*(z-v[3]-v[4]-v[7])/z)
   av,bv=disk(a[h]['stdout']),disk(b[h]['stdout']);v=[y-x for x,y in zip(av,bv)]
   io.append({'readMiBs':v[2]*512/1048576/dt,'writeMiBs':v[6]*512/1048576/dt,'busyPercent':v[9]/(dt*1000)*100})
  result['hosts'][h]={'dockerCpuAggregatePercent':dist(cpus),'dockerCpuNormalizedPercent':dist([x/2 for x in cpus]),'hostCpuBusyPercent':dist(host_cpu),'hostIowaitPercent':dist(wait),'perCoreBusyPercent':[dist(x) for x in percore],'containerMemoryMiB':dist([memory_mib(x['MemUsage']) for x in stats]),'hostAvailableMemoryMiB':dist([int(re.search(r'^MemAvailable:\s+(\d+)',x[h]['stdout'],re.M)[1])/1024 for x in s]),'disk':{k:dist([x[k] for x in io]) for k in io[0]},'blockIoFirstLast':[stats[0]['BlockIO'],stats[-1]['BlockIO']]}
 result['tomcatThreadsByConnector']=summarize_tomcat(s)
 result['appMetrics']={k:dist([metric(x['app']['stdout'],k) for x in s]) for k in ['hikaricp_connections_active','hikaricp_connections_pending','experiment_active_requests']}
 result['asyncLogQueue']={k:dist([metric(x['app']['stdout'],k) for x in s if re.search(r'^'+k+r'(?:\{|\s)',x['app']['stdout'],re.M)]) for k in ['experiment_log_queue_size','experiment_log_queue_remaining','experiment_log_queue_capacity']}
 result['connectionWaitMs']=dist([x['server']['connectionWaitMs'] for x in t if x['server']])
 result['rankSqlMs']=dist([q['durationMs'] for x in t for q in (x['server']['queries'] if x['server'] else []) if 'select ranked.product_id' in q['sql']])
 result['jdbcExecuteMs']=dist([sum(q['durationMs'] for q in (x['server']['queries'] if x['server'] else [])) for x in t if x['server']])
 result['gcPauseSecondsDuringSamples']=metric(s[-1]['app']['stdout'],'jvm_gc_pause_seconds_sum')-metric(s[0]['app']['stdout'],'jvm_gc_pause_seconds_sum')
 result['localK6CpuPercent']=dist([float(l.split()[1]) for x in s for l in x['local']['stdout'].splitlines() if len(l.split())>=5 and l.split()[4].split('/')[-1]=='k6'])
 (d/'resource-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'rps':r['config']['rps'],'status':r['status'],'runId':r['runId'],'appCpu':result['hosts']['app']['dockerCpuNormalizedPercent'],'dbCpu':result['hosts']['db']['dockerCpuNormalizedPercent'],'pool':result['appMetrics'],'connectionWaitMs':result['connectionWaitMs'],'k6':result['localK6CpuPercent']},ensure_ascii=False))
