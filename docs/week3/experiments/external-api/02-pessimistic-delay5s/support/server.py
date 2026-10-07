"""Start the app against the dedicated local experiment database."""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request

LAB = Path(__file__).resolve().parent.parent
ROOT = next(p for p in LAB.parents if (p / 'gradlew').exists())
RAW = ROOT / 'build/serializable-experiment'
RAW.mkdir(parents=True, exist_ok=True)
jar = max((ROOT / 'apps/commerce-api/build/libs').glob('*.jar'), key=lambda p: p.stat().st_mtime)
config = json.loads((LAB / 'config.json').read_text())
config.update({
    'spring.profiles.active': 'local', 'server.port': 18080,
    'datasource.mysql-jpa.main.jdbc-url': 'jdbc:mysql://127.0.0.1:13308/commerce_experiment',
    'datasource.mysql-jpa.main.username': 'application', 'datasource.mysql-jpa.main.password': 'application',
    'server.tomcat.accesslog.enabled': True, 'server.tomcat.accesslog.directory': str(RAW),
    'server.tomcat.accesslog.prefix': 'access', 'server.tomcat.accesslog.suffix': '.log',
    'server.tomcat.accesslog.rotate': False, 'server.tomcat.accesslog.buffered': False,
    'server.tomcat.accesslog.pattern': '%{X-Lab-Request-Id}i|%I|%s|%{begin:msec}t|%{end:msec}t',
    'logging.pattern.console': "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}|%thread|%level|%logger|%msg%n%ex",
    'datasource.mysql-jpa.main.maximum-pool-size': 40,
    'datasource.mysql-jpa.main.minimum-idle': 30,
    'datasource.mysql-jpa.main.connection-timeout': 3000,
})
env = os.environ.copy()
env['SPRING_APPLICATION_JSON'] = json.dumps(config)
with (RAW / 'app.log').open('w') as log:
    process = subprocess.Popen(['java', '-jar', str(jar)], cwd=ROOT, env=env,
                               stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
(RAW / 'app.pid').write_text(str(process.pid))
for _ in range(90):
    if process.poll() is not None:
        raise RuntimeError('App exited; see build/serializable-experiment/app.log')
    try:
        with urllib.request.urlopen('http://127.0.0.1:18080/swagger-ui/index.html', timeout=1) as response:
            if response.status == 200:
                print(f'Experiment app ready, PID={process.pid}', flush=True)
                break
    except Exception:
        time.sleep(1)
else:
    process.terminate()
    raise TimeoutError('App readiness timed out')
