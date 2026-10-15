"""Increase only purchase arrival rate; reuse collection, fixtures and both audits."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--experiment',type=Path,required=True);p.add_argument('--template',type=Path,required=True);p.add_argument('--ssh-key',required=True)
p.add_argument('--start',type=int,default=20);p.add_argument('--step',type=int,default=10);p.add_argument('--ceiling',type=int,default=100)
p.add_argument('--repeat-passing',action='store_true',help='Measure twice even when the first result passes')
a=p.parse_args();support=Path(__file__).resolve().parent;c=json.loads(a.template.read_text());results=[]
if list(a.experiment.glob('run-*')): raise RuntimeError('Use a fresh experiment; do not repeat writes')
def identity():
 return subprocess.check_output(['ssh','-i',a.ssh_key,'-o','BatchMode=yes',c['sshUser']+'@'+c['appHost'],'sudo docker inspect --format "{{.Id}} {{.State.StartedAt}} {{.Image}}" '+c['appContainer']],text=True,timeout=30).strip()
server=identity()
for rate in range(a.start,a.ceiling+1,a.step):
    passes=[]
    for repeat in (1,2):
        if identity()!=server: raise RuntimeError('Server restarted; stop to re-establish warmup conditions')
        root=a.experiment/f'run-{rate:03d}-{repeat}';root.mkdir(parents=True)
        config=json.loads(json.dumps(c));config['experimentId']=a.experiment.name+'-'+root.name
        config['purchase'].update(rps=rate);config['purchase'].pop('buyers',None)
        config['slo'].update(p99Ms=100,purchaseP99Ms=300,likeP99Ms=None)
        (root/'config.json').write_text(json.dumps(config,indent=2)+'\n')
        (root/'warmup-policy.json').write_text(json.dumps({'warmup':False,'serverIdentity':server,'reason':'reuse running JVM; retain preflight/recovery'},indent=2)+'\n')
        common=['--config',str(root/'config.json'),'--ssh-key',a.ssh_key]
        def call(name,args,log=None,check=True):
            with (root/(log or name+'.log')).open('w') as out:
                return subprocess.run([sys.executable,str(support/name),*args],stdout=out,stderr=subprocess.STDOUT,check=check).returncode
        call('mixed_purchase_fixture.py',common+['prepare'])
        call('mixed_purchase_fixture.py',common+['snapshot','--output',str(root/'before.json')])
        print(f'START purchase={rate}/s repeat={repeat}; read=1700/s likes=10/s 60s',flush=True)
        call('run_combined_slo.py',common+['--measure-only'],'measure.log',False)
        s=json.loads((root/'slo-runs.json').read_text())[-1]
        if not s['recovered'] or not all(s['collection'].values()) or s['exitCode']!=0: raise RuntimeError('Collection/recovery failed')
        call('mixed_purchase_fixture.py',common+['snapshot','--output',str(root/'after.json')])
        call('audit_mixed_purchase.py',[str(root)],'audit.log')
        call('audit_mixed_likes.py',common,'like-audit.log')
        if not json.loads((root/'purchase-audit.json').read_text())['passed']:raise RuntimeError('Purchase audit failed')
        call('summarize_mixed_run.py',[str(root)],'observations.log');call('summarize_stampede.py',[str(root)],'stampede.log')
        item={'run':root.name,'purchaseRps':rate,'readP99':s['latencyMs']['p(99)'],'purchaseP99':s['purchase']['latencyMs']['p(99)'],'dropped':s['dropped'],'passed':s['sloPassed']}
        results.append(item);passes.append(s['sloPassed']);(a.experiment/'steps.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(item),flush=True)
        if repeat==1 and s['sloPassed'] and not a.repeat_passing:break
    if not all(passes):
        print('STOP: target breach observed; confirmation recorded. No further escalation.',flush=True);break
