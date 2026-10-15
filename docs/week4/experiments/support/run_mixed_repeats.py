"""Repeat mixed experiments using the shared warmup, load and audit tools."""
import argparse
import json
import subprocess
import shlex
import sys
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--experiment',type=Path,required=True);p.add_argument('--ssh-key',required=True)
p.add_argument('--warmup-each-run',action='store_true',help='Explicitly reproduce historical per-round warmup')
a=p.parse_args();support=Path(__file__).resolve().parent
previous_server = None
for root in sorted(a.experiment.glob('run-*')):
    if list((root/'results').glob('*-measure-*')):
        raise RuntimeError('Measurement already exists; use fresh run directories and buyers')
    config=json.loads((root/'config.json').read_text())
    server=subprocess.check_output(['ssh','-i',a.ssh_key,'-o','BatchMode=yes','-o','ConnectTimeout=5',
        config['sshUser']+'@'+config['appHost'],
        'sudo docker inspect --format '+shlex.quote('{{.Id}} {{.State.StartedAt}} {{.Image}}')+' '+shlex.quote(config['appContainer'])],text=True,timeout=30).strip()
    common=['--config',str(root/'config.json'),'--ssh-key',a.ssh_key]
    def call(name,args,log=None):
        command=[sys.executable,str(support/name),*args]
        if log:
            with (root/log).open('w') as output: return subprocess.run(command,stdout=output,stderr=subprocess.STDOUT).returncode
        return subprocess.run(command,check=True).returncode
    warmup = a.warmup_each_run or server != previous_server
    (root/'warmup-policy.json').write_text(json.dumps({'warmup':warmup,'serverIdentity':server,
        'reason':'explicit per-round warmup' if a.warmup_each_run else ('initial or restarted server' if warmup else 'same server; recovery checks only')},indent=2)+'\n')
    if warmup:
        print(f'{root.name}: WARMUP',flush=True)
        code=call('run_combined_slo.py',common+['--warmup-only','--continue-after-warmup-failure'],'warmup.log')
        if code: raise RuntimeError('Warmup collection/recovery failed; stop before next load')
    else:
        print(f'{root.name}: SKIP WARMUP; retain preflight/recovery checks',flush=True)
    previous_server = server
    call('mixed_purchase_fixture.py',common+['snapshot','--output',str(root/'before.json')])
    print(f"{root.name}: START MIXED 1700 reads/s + 10 purchases/s + {config.get('likes', {}).get('pairRps', 0) * 2} like writes/s for 60s",flush=True)
    code=call('run_combined_slo.py',common+['--measure-only'],'measure.log')
    runs=json.loads((root/'slo-runs.json').read_text());run=runs[-1]
    if run['phase']!='measure' or not run['recovered'] or not all(run['collection'].values()) or run['exitCode']!=0:
        raise RuntimeError('Measurement collection/recovery failed; artifacts preserved')
    call('mixed_purchase_fixture.py',common+['snapshot','--output',str(root/'after.json')])
    call('audit_mixed_purchase.py',[str(root)],'audit.log')
    if not json.loads((root/'purchase-audit.json').read_text())['passed']: raise RuntimeError('Purchase audit failed')
    if config.get('likes', {}).get('pairRps', 0):
        call('audit_mixed_likes.py',common,'like-audit.log')
    call('summarize_mixed_run.py',[str(root)],'observations.log')
    call('summarize_stampede.py',[str(root)],'stampede.log')
    print(json.dumps({'run':root.name,'runId':run['runId'],'readP99':run['latencyMs']['p(99)'],
        'purchaseP99':run['purchase']['latencyMs']['p(99)'],'readSuccess':run['successRate'],
        'purchaseSuccess':run['purchase']['successRate'],'dropped':run['dropped'],
        'sloPassed':run['sloPassed'],'auditPassed':True}),flush=True)
