"""Three-call screen workload, reusing RemoteRunner capture and recovery."""
import argparse,json,shlex,subprocess
from pathlib import Path
from remote_runner import RemoteRunner
from run import HERE,save

class ScreenRunner(RemoteRunner):
    def __init__(self, config, key):
        super().__init__(config,key)
        items=self.expected['data']['content']
        self.expected['counts']=[{'productId':p['id'],'likeCount':p['likeCount']} for p in sorted(items,key=lambda p:p['id'])]
        self.expected['stocks']=[{'productId':p['id'],'soldOut':p['soldOut']} for p in sorted(items,key=lambda p:p['id'])]
        self.expected['data']['content']=[{k:v for k,v in p.items() if k not in ('likeCount','soldOut')} for p in items]

    def api_check(self):
        base=self.base['loadBaseUrl'];ids=','.join(str(p['productId']) for p in self.expected['counts'])
        paths={'data':self.url.replace(self.base['baseUrl'],base,1),'counts':base+'/api/v1/products/like-counts?ids='+ids,'stocks':base+'/api/v1/products/stocks?ids='+ids}
        checks={}
        for name,url in paths.items():
            r=self.ssh(self.load_host,'curl -fsS --max-time 8 -H '+shlex.quote('X-USER-ID: '+str(self.base['input']['userId']))+' '+shlex.quote(url))
            try:checks[name]=r['exitCode']==0 and json.loads(r['stdout'])['data']==self.expected[name]
            except (ValueError,KeyError):checks[name]=False
        return {'ok':all(checks.values()),'checks':checks}

    def launch_load(self,out,stream):
        remote=self.remote_dir(out);cfg=json.loads((out/'config.json').read_text())
        cfg['url']=cfg['url'].replace(self.base['baseUrl'],self.base['loadBaseUrl'],1)
        save(out/'load-config.json',cfg)
        self.ssh(self.load_host,'mkdir -p '+shlex.quote(remote))
        self.copy([str(out/'load-config.json'),str(out/'expected.json'),str(HERE/'product_screen.js')],self.base['sshUser']+'@'+self.load_host+':'+remote+'/')
        script='cd '+shlex.quote(remote)+' && exec timeout --signal=TERM --kill-after=5s '+str(cfg['durationSeconds']+cfg['gracefulStopSeconds']+20)+'s env CONFIG_FILE=load-config.json EXPECTED_FILE=expected.json SUMMARY_FILE=summary.json /home/ubuntu/week4-load/bin/k6 run --quiet --log-format raw --console-output client.log product_screen.js'
        return subprocess.Popen(['ssh','-i',self.key,'-o','BatchMode=yes',self.base['sshUser']+'@'+self.load_host,script],stdout=stream,stderr=subprocess.STDOUT)

    def cache_state(self):
        # Read-only TTL snapshot. No expiry extension, flush, or application changes.
        ids=','.join(str(p['productId']) for p in self.expected['counts'])
        patterns=['products::v3:products?sort=latest&brand=all&page=0&size=20:rev=*',
                  'like-counts::v3:products/like-counts?ids='+ids+':rev=*',
                  'stocks::v3:products/stocks?ids='+ids+':rev=*']
        result={}
        for pattern in patterns:
            found=self.ssh(self.base['dbHost'],'sudo docker exec week4-db-redis-master-1 redis-cli --scan --pattern '+shlex.quote(pattern))
            for key in found['stdout'].splitlines():
                ttl=self.ssh(self.base['dbHost'],'sudo docker exec week4-db-redis-master-1 redis-cli PTTL '+shlex.quote(key))
                result[key]=ttl['stdout'].strip()
        return result

def main():
    p=argparse.ArgumentParser();p.add_argument('--config',type=Path,required=True);p.add_argument('--ssh-key',required=True);a=p.parse_args()
    r=ScreenRunner(a.config,a.ssh_key);runs=[]
    for rate,seconds,phase in [(300,20,'warmup'),(700,20,'warmup'),(1700,60,'measure')]:
        if not r.api_check()['ok']:raise RuntimeError('Seed response comparison failed')
        before=r.cache_state();print(f'START {phase}: {rate} screen visits/s for {seconds}s',flush=True)
        result=r.run(rate,seconds,phase,defer_aggregation=True)
        out=r.experiment/'results'/result['runId'];save(out/'cache-ttl.json',{'before':before,'after':r.cache_state()})
        m=json.loads((out/'summary.json').read_text())['metrics']
        def v(name,field='count'):return m.get(name,{}).get('values',{}).get(field,0)
        summary={'runId':result['runId'],'phase':phase,'targetVisitsPerSecond':rate,'durationSeconds':seconds,
                 'started':v('screen_started'),'completed':v('screen_completed'),'dropped':v('dropped_iterations'),
                 'successRate':v('screen_success','rate'),'mismatches':v('screen_mismatch'),
                 'httpRequests':v('http_reqs'),'httpRps':v('http_reqs','rate'),
                 'durationMs':m.get('screen_duration_ms',{}).get('values',{}),
                 'successDurationMs':m.get('screen_success_duration_ms',{}).get('values',{}),
                 'apiDurationMs':{k:v['values'] for k,v in m.items() if k.startswith('http_req_duration{')},
                 'recovered':result['recovery']['ok'],'exitCode':result['exitCode']}
        summary['sloPassed']=(summary['durationMs'].get('p(99)',float('inf'))<=200 and summary['successRate']==1 and summary['dropped']==0 and summary['started']==summary['completed'] and summary['started']>0 and summary['exitCode']==0 and result['status']=='collected')
        save(out/'screen-result.json',summary);runs.append(summary);save(r.experiment/'screen-runs.json',runs);print(json.dumps(summary),flush=True)
        if result['status']!='collected' or not summary['recovered'] or summary['successRate']!=1 or summary['dropped'] or summary['exitCode']:
            raise RuntimeError('Stop after unsuccessful load or recovery; artifacts preserved')
if __name__=='__main__':main()
