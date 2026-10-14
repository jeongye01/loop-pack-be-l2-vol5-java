"""One read-only load run with three HotSpot SIGQUIT thread snapshots."""
import argparse
import json
import threading
from datetime import datetime, timezone
from pathlib import Path
from remote_runner import RemoteRunner
from run import save

class DumpRunner(RemoteRunner):
    def launch_load(self, out, stream):
        process = super().launch_load(out, stream)
        config = json.loads((out/'config.json').read_text())
        if config['phase'] != 'measure':
            return process
        def capture():
            captures=[]
            for second in (15,30,45):
                if self.dump_stop.wait(15): break
                if process.poll() is not None: break
                before=datetime.now(timezone.utc).isoformat()
                result=self.ssh(self.base['appHost'],
                    'sudo docker kill --signal=QUIT '+self.base['appContainer'])
                captures.append({'at':before,'nominalSecondsAfterLaunch':second,'signal':result})
                save(out/'thread-dump-signals.json',captures)
        self.dump_thread=threading.Thread(target=capture,daemon=True)
        self.dump_thread.start()
        return process

    def collect_load(self, out):
        self.dump_stop.set()
        if self.dump_thread: self.dump_thread.join(timeout=30)
        super().collect_load(out)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--ssh-key',required=True)
    args=parser.parse_args()
    runner=DumpRunner(args.config,args.ssh_key);root=runner.experiment;runs=[]
    for rate,seconds,phase in [(700,30,'warmup'),(1700,60,'measure')]:
        runner.dump_stop=threading.Event();runner.dump_thread=None
        if not runner.api_check()['ok']:raise RuntimeError('Pre-check failed')
        key=runner.base['cache']['key']
        result=runner.ssh(runner.base['dbHost'],'sudo docker exec week4-db-redis-master-1 redis-cli EXPIRE '+key+' 600')
        if result['exitCode'] or result['stdout'].strip()!='1':raise RuntimeError('Cache key missing')
        print('STARTING',phase,rate,flush=True)
        result=runner.run(rate,seconds,phase,1,defer_aggregation=True)
        runs.append(result['runId']);save(root/'all-runs.json',runs)
        if phase=='measure':save(root/'comparison-runs.json',[result['runId']])
        if result['exitCode']!=0 or not result['recovery']['ok'] or not all(result['collection'].values()):
            raise RuntimeError('Capture/recovery failed')

if __name__=='__main__':main()
