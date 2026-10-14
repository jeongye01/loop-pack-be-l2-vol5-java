"""Run the shared k6 workload on a separate EC2; keep credentials on the controller."""
import json
import re
import shlex
import subprocess
from run import Runner, HERE, command, save


class RemoteRunner(Runner):
    def __init__(self, config_path, key):
        super().__init__(config_path, key)
        self.load_host = self.base['loadHost']
        if not re.fullmatch(r'[a-zA-Z0-9_.-]+', self.load_host):
            raise ValueError('Invalid loadHost')

    def remote_dir(self, out):
        return '/home/ubuntu/week4-load/runs/' + out.name

    def copy(self, sources, destination):
        result = command(['scp', '-i', self.key, '-o', 'BatchMode=yes', *sources, destination], timeout=120)
        if result['exitCode']:
            raise RuntimeError('Remote artifact transfer failed: ' + result['stderr'])

    def launch_load(self, out, stream):
        remote = self.remote_dir(out)
        config = json.loads((out/'config.json').read_text())
        config['url'] = config['url'].replace(self.base['baseUrl'], self.base['loadBaseUrl'], 1)
        save(out/'load-config.json', config)
        created = self.ssh(self.load_host, 'mkdir -p ' + shlex.quote(remote))
        if created['exitCode']:
            raise RuntimeError('Could not create remote run directory')
        self.copy([str(out/'load-config.json'), str(out/'expected.json'), str(HERE/'products.js')],
                  self.base['sshUser']+'@'+self.load_host+':'+remote+'/')
        script = ('cd '+shlex.quote(remote)+' && exec timeout --signal=TERM --kill-after=5s '
                  +str(config['durationSeconds']+config['gracefulStopSeconds']+20)+'s '
                  +'env CONFIG_FILE=load-config.json EXPECTED_FILE=expected.json SUMMARY_FILE=summary.json '
                  +'/home/ubuntu/week4-load/bin/k6 run --quiet --log-format raw --console-output client.log products.js')
        return subprocess.Popen(['ssh','-i',self.key,'-o','BatchMode=yes','-o','ConnectTimeout=5',
                                 self.base['sshUser']+'@'+self.load_host,script],
                                stdout=stream,stderr=subprocess.STDOUT)

    def collect_load(self, out):
        remote = self.remote_dir(out)
        for name in ('client.log','summary.json'):
            self.copy([self.base['sshUser']+'@'+self.load_host+':'+remote+'/'+name], str(out/name))

    def sample(self):
        result = super().sample()
        result['controller'] = result['local']
        result['local'] = self.ssh(self.load_host,
            'ps -axo pid,pcpu,pmem,rss,comm; cat /proc/stat; cat /proc/meminfo; cat /proc/loadavg; cat /proc/diskstats')
        result['loadLocation'] = self.load_host
        return result

    def snapshot(self):
        result = super().snapshot()
        result['loadGenerator'] = self.ssh(self.load_host,
            'date -u; uname -a; cat /etc/os-release; nproc; free -b; df -B1 /; '
            '/home/ubuntu/week4-load/bin/k6 version; sha256sum /home/ubuntu/week4-load/bin/k6')
        if result['loadGenerator']['exitCode']:
            raise RuntimeError('Load generator snapshot failed')
        result['k6'] = self.ssh(self.load_host, '/home/ubuntu/week4-load/bin/k6 version')
        return result
