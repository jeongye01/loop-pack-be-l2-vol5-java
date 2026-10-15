"""No network/load: verify warmup policy and recovery gates in the real orchestrator."""
import contextlib
import io
import json
import runpy
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

class MixedRepeatProcessTest(unittest.TestCase):
    def exercise(self, identities, each=False, recovery=True):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for i in range(1,4):
                p=root/f'run-{i}';p.mkdir();(p/'config.json').write_text(json.dumps({'sshUser':'test','appHost':'test','appContainer':'app'}))
            calls=[]
            def run(command, **kwargs):
                name=Path(command[1]).name; calls.append(command)
                if name=='run_combined_slo.py' and '--measure-only' in command:
                    out=Path(command[command.index('--config')+1]).parent
                    result={'phase':'measure','recovered':recovery,'collection':{'ok':True},'exitCode':0,'runId':'mock',
                        'latencyMs':{'p(99)':1},'purchase':{'latencyMs':{'p(99)':2},'successRate':1},
                        'successRate':1,'dropped':0,'sloPassed':True}
                    (out/'slo-runs.json').write_text(json.dumps([result]))
                if name=='audit_mixed_purchase.py': (Path(command[2])/'purchase-audit.json').write_text('{"passed":true}')
                return SimpleNamespace(returncode=0)
            args=['script','--experiment',str(root),'--ssh-key','fake']+(['--warmup-each-run'] if each else [])
            with patch.object(sys,'argv',args),patch('subprocess.check_output',side_effect=identities),patch('subprocess.run',side_effect=run),contextlib.redirect_stdout(io.StringIO()):
                if recovery: runpy.run_path(str(Path(__file__).with_name('run_mixed_repeats.py')),run_name='__main__')
                else:
                    with self.assertRaisesRegex(RuntimeError,'collection/recovery failed'):
                        runpy.run_path(str(Path(__file__).with_name('run_mixed_repeats.py')),run_name='__main__')
            return sum('--warmup-only' in c for c in calls),sum('--measure-only' in c for c in calls)

    def test_same_server_warms_once_but_measures_three_times(self):
        self.assertEqual(self.exercise(['same']*3),(1,3))
    def test_restart_warms_again(self):
        self.assertEqual(self.exercise(['old','new','new']),(2,3))
    def test_historical_option_warms_each_time(self):
        self.assertEqual(self.exercise(['same']*3,each=True),(3,3))
    def test_recovery_failure_stops_before_next_round(self):
        self.assertEqual(self.exercise(['same']*3,recovery=False),(1,1))

if __name__=='__main__': unittest.main()
