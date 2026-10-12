"""Read-only EC2 RPS discovery; reuse the existing request and CSV validation."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
from datetime import datetime, timezone
import probe

BASE = 'http://3.106.241.33:8080'
KEY = '/Users/jeong-yewon/dev/ssh/week4.pem'
APP = '3.106.241.33'
DB = '16.176.204.86'
RATES = [1, 2, 3, 5, 6, 8]
DURATION = 30
VUS = 100
original_command = probe.command


def ssh(host, script):
    return original_command(['ssh', '-i', KEY, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=5',
                             'ubuntu@' + host, script])


def remote_command(args):
    if args[:2] == ['docker', 'stats']:
        return {host: ssh(host, "date -u; uptime; free -m; "
                         "sudo docker stats --no-stream --format '{{json .}}'")
                for host in (APP, DB)}
    return original_command(args)


def scrape(path):
    result = ssh(APP, 'curl --fail --silent --max-time 5 http://127.0.0.1:8081/actuator/prometheus')
    if result['exitCode']:
        path.write_text(json.dumps(result))
        raise RuntimeError('EC2 metrics collection failed')
    path.write_text(result['stdout'])


def recover(folder):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        path = folder / 'metrics-recovery.txt'
        scrape(path)
        lines = path.read_text().splitlines()
        values = [float(line.rsplit(' ', 1)[1]) for line in lines
                  if line.startswith(('hikaricp_connections_active{', 'hikaricp_connections_pending{'))]
        if len(values) >= 2 and all(value == 0 for value in values):
            probe.baseline()  # Confirm that a fresh read still matches all CSV fields.
            return True
        time.sleep(3)
    return False


def stop(result):
    return (result['exitCode'] != 0 or result['failureRate'] > 0
            or result['invalidResponseRate'] > 0 or result['dropped'] > 0)


def main():
    os.environ['BASE_URL'] = BASE
    probe.URL = BASE + '/api/v1/products?brandId=7&sort=price_asc&page=0&size=20'
    probe.command = remote_command
    probe.scrape = scrape
    out = probe.HERE / 'results' / ('ec2-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
    out.mkdir(parents=True)
    probe.save(out / 'expected.json', probe.baseline())
    env = {'startedAt': datetime.now(timezone.utc).isoformat(), 'url': probe.URL,
           'rates': RATES, 'durationSeconds': DURATION, 'warmupSeconds': 15, 'vus': VUS,
           'timeoutSeconds': 5, 'gracefulStopSeconds': 10,
           'stopRule': 'Stop increasing at any HTTP failure, invalid response, or dropped iteration',
           'git': original_command(['git', 'rev-parse', 'HEAD']),
           'gitStatus': original_command(['git', 'status', '--short']),
           'k6': original_command(['k6', 'version']),
           'hardware': original_command(['sysctl', '-n', 'hw.model', 'hw.ncpu', 'hw.memsize']),
           'datasetManifestSha256': hashlib.sha256((probe.DATA / 'manifest.json').read_bytes()).hexdigest(),
           'scriptsSha256': {name: hashlib.sha256((probe.HERE / name).read_bytes()).hexdigest()
                            for name in ['ec2_probe.py', 'probe.py', 'products.js']},
           'appConfig': {'profiles': 'local,docker', 'ddlAuto': 'none', 'hikariMax': 40,
                         'hikariMin': 30, 'connectionTimeoutMs': 3000, 'tomcatMaxThreads': 200},
           'remote': {host: ssh(host, 'uname -a; free -m; df -h /; sudo docker version --format "{{.Server.Version}}"; '
                               'sudo docker ps --format "{{.Names}} {{.Image}}"') for host in [APP, DB]},
           'artifact': ssh(APP, 'cd ~/week4 && sha256sum app.jar && sudo docker image inspect '
                           'commerce-api:week4-20261013 --format "{{.Id}} {{.Architecture}}"'),
           'notes': ['Local k6 to Sydney EC2 over public network.',
                     'Resource samples include SSH and docker-stats overhead; timestamps are actual samples.',
                     'invalid_response also includes transport/HTTP failures in the reused script.',
                     'No per-request server/SQL traces; no correctness claims based on metrics alone.']}
    probe.save(out / 'environment.json', env)
    print('Results: ' + str(out), flush=True)
    warmup = probe.run(out, 'warmup', 1, 15, VUS)
    if stop(warmup):
        probe.save(out / 'results.json', [])
        print('Warmup failed; stopping.', flush=True)
        return
    results = []
    for index, rate in enumerate(RATES):
        if not recover(out):
            print('Recovery not confirmed within 60 seconds; stopping.', flush=True)
            break
        result = probe.run(out, f'{index+1:02d}-{rate}rps', rate, DURATION, VUS)
        results.append(result)
        probe.save(out / 'results.json', results)
        if stop(result):
            print('Stop increasing load: error, mismatch, or dropped iteration.', flush=True)
            break
        time.sleep(5)
    recovered = recover(out)
    probe.save(out / 'recovery.json', {'recovered': recovered, 'finishedAt': datetime.now(timezone.utc).isoformat()})
    errors = ssh(APP, 'cd ~/week4 && sudo docker compose --env-file app.env -f app-compose.yml logs '
                 '--no-color --since ' + env['startedAt'])
    (out / 'server.log').write_text(errors['stdout'] + errors['stderr'])
    print('Recovery: ' + str(recovered), flush=True)


if __name__ == '__main__':
    main()
