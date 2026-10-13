"""Read-only EC2 experiment runner. No deploy, reset, DDL, or data mutation."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import urllib.parse
import urllib.request
import uuid
from report import aggregate, events, write_report

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def command(args, timeout=25):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        return {'exitCode':r.returncode, 'stdout':r.stdout, 'stderr':r.stderr}
    except (subprocess.TimeoutExpired, OSError) as e:
        return {'exitCode':-1, 'stdout':'', 'stderr':str(e)}


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def expected_page(dataset, query):
    manifest = json.loads((dataset/'manifest.json').read_text())
    for name, entry in manifest['files'].items():
        if sha(dataset/name) != entry['sha256']:
            raise ValueError(f'Dataset checksum mismatch: {name}')
    def rows(name):
        with (dataset/name).open(newline='') as f:
            yield from csv.DictReader(f)
    brands = {int(b['id']):b['name'] for b in rows('brands.csv') if not b['deleted_at']}
    stock = {int(s['product_id']):int(s['quantity']) for s in rows('stocks.csv') if not s['deleted_at']}
    likes = Counter(int(l['product_id']) for l in rows('likes.csv') if not l['deleted_at'])
    products = [p for p in rows('products.csv') if not p['deleted_at'] and int(p['brand_id']) in brands
                and (query.get('brandId') is None or int(p['brand_id'])==query['brandId'])]
    sort = query['sort']
    if sort=='price_asc': products.sort(key=lambda p:(int(p['price']),int(p['id'])))
    elif sort=='likes_desc': products.sort(key=lambda p:(-likes[int(p['id'])],int(p['id'])))
    elif sort=='latest':
        products.sort(key=lambda p:int(p['id']))
        products.sort(key=lambda p:p['created_at'], reverse=True)
    else: raise ValueError('Unsupported sort')
    start=query['page']*query['size']
    return {'data':{'content':[{'id':int(p['id']),'name':p['name'],'price':int(p['price']),
            'brand':{'id':int(p['brand_id']),'name':brands[int(p['brand_id'])]},
            'likeCount':likes[int(p['id'])],'soldOut':stock[int(p['id'])]==0}
            for p in products[start:start+query['size']]],
            'page':query['page'],'size':query['size'],'last':(query['page']+1)*query['size'] >= len(products)}}


class Runner:
    def __init__(self, config_path, key):
        self.experiment = config_path.resolve().parent
        self.base = json.loads(config_path.read_text())
        self.key = str(Path(key).expanduser().resolve())
        if not Path(self.key).is_file(): raise ValueError('SSH key missing')
        for name in ('appHost','dbHost','sshUser','appContainer','dbContainer','database'):
            if not re.fullmatch(r'[a-zA-Z0-9_.-]+', self.base[name]): raise ValueError('Invalid '+name)
        self.dataset = ROOT/self.base['dataset']
        self.expected = expected_page(self.dataset,self.base['input'])
        params = {k:v for k,v in self.base['input'].items() if k!='userId'}
        self.url = self.base['baseUrl']+'/api/v1/products?'+urllib.parse.urlencode(params)
        self.git = command(['git','rev-parse','HEAD'])['stdout'].strip()

    def ssh(self, host, script, timeout=25):
        return command(['ssh','-i',self.key,'-o','BatchMode=yes','-o','ConnectTimeout=5',
                        self.base['sshUser']+'@'+host,script],timeout)

    def api_check(self):
        request = urllib.request.Request(self.url,headers={'X-USER-ID':str(self.base['input']['userId'])})
        start=time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=self.base['timeoutSeconds']) as response:
                body=json.load(response)
            return {'ok':body.get('meta',{}).get('result')=='SUCCESS' and body.get('data')==self.expected['data'],
                    'latencyMs':(time.monotonic()-start)*1000}
        except Exception as e: return {'ok':False,'error':str(e)}

    def sample(self):
        script = ('date -u +%Y-%m-%dT%H:%M:%SZ; cat /proc/loadavg; cat /proc/stat; '
                  'cat /proc/meminfo; cat /proc/diskstats; '
                  "sudo docker stats --no-stream --format '{{json .}}'")
        with ThreadPoolExecutor(max_workers=3) as pool:
            app=pool.submit(self.ssh,self.base['appHost'],script+
                '; echo METRICS_START; curl --fail --silent --max-time 5 http://127.0.0.1:8081/actuator/prometheus')
            db=pool.submit(self.ssh,self.base['dbHost'],script)
            local=pool.submit(command,['ps','-axo','pid,pcpu,pmem,rss,comm'])
            return {'at':datetime.now(timezone.utc).isoformat(), 'app':app.result(),
                    'db':db.result(),'local':local.result()}

    def snapshot(self):
        script = ('date -u; uname -a; cat /etc/os-release; nproc; free -b; df -B1 /; '
                  'sudo docker version --format "{{.Server.Version}}"; '
                  'sudo docker ps --format "{{.Names}} {{.Image}}"')
        app=self.base['appContainer']
        db=self.base['dbContainer']
        sql=('SELECT VERSION(), @@innodb_buffer_pool_size, @@max_connections, @@transaction_isolation; '
             'SELECT COUNT(*) FROM brand; SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM product; '
             'SELECT COUNT(*) FROM stock; SELECT COUNT(*) FROM product_like; '
             'SHOW CREATE TABLE product; SHOW CREATE TABLE product_like; SHOW INDEX FROM product; '
             'SHOW INDEX FROM product_like;')
        inner='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B '+self.base['database']+' -e '+shlex.quote(sql)
        with ThreadPoolExecutor(max_workers=2) as pool:
            a=pool.submit(self.ssh,self.base['appHost'],script+
                f'; sudo docker inspect {app} --format '+shlex.quote('{{.Image}} cpu={{.HostConfig.NanoCpus}} memory={{.HostConfig.Memory}}')+
                f'; sudo docker exec {app} java -XX:+PrintFlagsFinal -version 2>&1 | '+
                "awk '/MaxHeapSize|InitialHeapSize|openjdk|Temurin/'; sha256sum ~/week4/app.jar")
            b=pool.submit(self.ssh,self.base['dbHost'],script+f'; sudo docker exec {db} sh -c '+shlex.quote(inner))
            return {'at':datetime.now(timezone.utc).isoformat(),'app':a.result(),'db':b.result(),
                    'local':command(['sysctl','-n','hw.model','hw.ncpu','hw.memsize']),
                    'os':command(['sw_vers']), 'k6':command(['k6','version'])}

    def recovered_sample(self, sample):
        if any(sample[h]['exitCode'] for h in ('app','db')): return False
        text=sample['app']['stdout']
        def metric(name):
            return [float(v) for v in re.findall(r'^'+name+r'(?:\{[^\n]*\})?\s+([0-9.eE+-]+)',text,re.M)]
        for key in ('hikaricp_connections_active','hikaricp_connections_pending','experiment_active_requests'):
            values=metric(key)
            if not values or any(v!=0 for v in values): return False
        for host, container in [('app',self.base['appContainer']),('db',self.base['dbContainer'])]:
            output=sample[host]['stdout']
            mem=re.search(r'^MemAvailable:\s+(\d+) kB',output,re.M)
            if not mem or int(mem[1])*1024 < self.base['recoveryMinAvailableMemoryBytes']: return False
            stats=[json.loads(line) for line in output.splitlines() if line.startswith('{"')]
            target=next((s for s in stats if s.get('Name')==container),None)
            if not target or float(target['CPUPerc'].rstrip('%')) > self.base['recoveryMaxContainerCpuPercent']: return False
        return True

    def recover(self):
        deadline=time.monotonic()+self.base['recoverySeconds']
        samples=[]
        while time.monotonic()<deadline:
            sample=self.sample(); samples.append(sample)
            if self.recovered_sample(sample):
                check=self.api_check()
                if check['ok']: return {'ok':True,'samples':samples,'response':check}
            time.sleep(2)
        return {'ok':False,'samples':samples,'reason':'Recovery deadline exceeded'}

    def run(self, rps, seconds, phase='measure', repeat=1):
        if rps<=0 or seconds<=0 or round(rps*100)!=rps*100:
            raise ValueError('Use positive RPS with at most two decimal places')
        if rps>self.base['maximumDiscoveryRps']: raise ValueError('Discovery RPS safety cap exceeded')
        run_id=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+phase+'-'+str(rps).replace('.','p')+'-'+uuid.uuid4().hex[:6]
        out=self.experiment/'results'/run_id; out.mkdir(parents=True)
        config=dict(self.base,runId=run_id,phase=phase,repeat=repeat,rps=rps,durationSeconds=seconds,
                    url=self.url,userId=self.base['input']['userId'])
        result={'schemaVersion':1,'runId':run_id,'experimentId':self.base['experimentId'],
                'gitSha':self.git,'status':'running','config':config,
                'startedAt':datetime.now(timezone.utc).isoformat()}
        save(out/'config.json',config); save(out/'expected.json',self.expected); save(out/'result.json',result)
        environment={'start':self.snapshot(),'gitStatus':command(['git','status','--short']),
                     'datasetManifestSha256':sha(self.dataset/'manifest.json'),
                     'scriptsSha256':{p.name:sha(p) for p in HERE.glob('*') if p.is_file()},
                     'notes':['OS snapshots select fields; secrets excluded.',
                              'Local k6 over public network. No cache in product path.',
                              'SQL timings cover JDBC execute calls, not result hydration or pool acquisition.',
                              'Nearest-rank percentiles; millisecond client clock resolution.']}
        save(out/'environment.json',environment)
        process=None; samples=[]; failure=None; recovery={'ok':False,'reason':'Not attempted'}
        since=datetime.now(timezone.utc).isoformat()
        try:
            before=self.recover(); save(out/'preflight.json',before)
            if not before['ok']: raise RuntimeError('Preflight recovery failed')
            env=dict(os.environ,CONFIG_FILE=str(out/'config.json'),EXPECTED_FILE=str(out/'expected.json'),
                     SUMMARY_FILE=str(out/'summary.json'))
            with (out/'k6.log').open('w') as stream:
                process=subprocess.Popen(['k6','run','--quiet','--log-format','raw','--console-output',str(out/'client.log'),
                                          str(HERE/'products.js')],env=env,stdout=stream,stderr=subprocess.STDOUT)
                deadline=time.monotonic()+seconds+config['gracefulStopSeconds']+20
                while process.poll() is None:
                    if time.monotonic()>deadline: raise TimeoutError('k6 bounded runtime exceeded')
                    next_sample=time.monotonic()+config['sampleIntervalSeconds']
                    samples.append(self.sample())
                    # Keep collecting after a failed sample and retain its actual timestamp.
                    time.sleep(max(0,next_sample-time.monotonic()))
            recovery=self.recover()
        except (Exception,KeyboardInterrupt) as e:
            failure=f'{type(e).__name__}: {e}'
        finally:
            if process and process.poll() is None:
                process.terminate()
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
            if not recovery['ok']:
                try: recovery=self.recover()
                except Exception as e: recovery={'ok':False,'reason':str(e)}
            save(out/'resources.json',samples)
            remote=self.ssh(self.base['appHost'],f'sudo docker logs --since {shlex.quote(since)} '+self.base['appContainer'],timeout=40)
            (out/'server.log').write_text(remote['stdout']+remote['stderr'])
            environment['end']=self.snapshot(); save(out/'environment.json',environment)
            result.update(finishedAt=datetime.now(timezone.utc).isoformat(),recovery=recovery,
                          exitCode=process.returncode if process else None,stopReason=failure,
                          artifacts={'traces':'traces.jsonl','server':'server.log','resources':'resources.json'})
            try:
                client=events(out/'client.log','TRACE_JSON ')
                server=events(out/'server.log','EXPERIMENT_JSON ')
                summary=json.loads((out/'summary.json').read_text()) if (out/'summary.json').exists() else None
                traces, report=aggregate(config,client,server,summary)
                with (out/'traces.jsonl').open('w') as stream:
                    for trace in traces: stream.write(json.dumps(trace,ensure_ascii=False)+'\n')
                result.update(report)
                counts=report['requests']; valid=report['validation']
                sample_ok=bool(samples) and all(s[h]['exitCode']==0 for s in samples for h in ('app','db','local'))
                environment_ok=all(environment[t][h]['exitCode']==0 for t in ('start','end') for h in ('app','db'))
                good=(not failure and result['exitCode']==0 and recovery['ok'] and remote['exitCode']==0
                      and sample_ok and environment_ok and valid['measurementComplete']
                      and valid['summaryPresent'] and valid['summaryCountMatches'] and not valid['serverUnmatchedIds']
                      and counts['dropped']==0 and counts['incomplete']==0
                      and counts['completed']>0 and counts['byOutcome'].get('success',0)==counts['completed'])
                result['status']='passed' if good else 'failed'
                result['validation'].update(samplesComplete=sample_ok,environmentComplete=environment_ok,
                                             serverLogsCollected=remote['exitCode']==0)
                if not good and not failure: result['stopReason']='See request outcomes, validation and recovery'
            except Exception as e:
                result.update(status='failed',stopReason=f'{failure or ""}; aggregation: {e}')
            save(out/'result.json',result); write_report(self.experiment)
        print(json.dumps({'runId':run_id,'status':result['status'],'rps':rps,'phase':phase,
                          'requests':result.get('requests'),'metrics':result.get('metrics'),
                          'recovered':recovery['ok']},ensure_ascii=False),flush=True)
        return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--ssh-key',default=os.environ.get('WEEK4_SSH_KEY'))
    parser.add_argument('--rps',type=float,required=True)
    parser.add_argument('--seconds',type=int)
    parser.add_argument('--phase',choices=['diagnostic','warmup','measure'],default='measure')
    parser.add_argument('--repeat',type=int,default=1)
    args=parser.parse_args()
    if not args.ssh_key: parser.error('--ssh-key or WEEK4_SSH_KEY required')
    runner=Runner(args.config,args.ssh_key)
    seconds=args.seconds or runner.base['durationSeconds']
    result=runner.run(args.rps,seconds,args.phase,args.repeat)
    raise SystemExit(0 if result['status']=='passed' else 1)


if __name__=='__main__': main()
