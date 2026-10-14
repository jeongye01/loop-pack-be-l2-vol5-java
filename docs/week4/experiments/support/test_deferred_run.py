import json
import tempfile
import unittest
from pathlib import Path
from run import aggregate_run

class DeferredRunTest(unittest.TestCase):
    def fixture(self, directory, outcome='success', sample_ok=True, duplicate=False):
        p=Path(directory)
        result={'status':'collected','config':{'runId':'r','rps':1,'durationSeconds':1},
                'exitCode':0,'stopReason':None,'recovery':{'ok':True},
                'collection':{'samplesComplete':sample_ok,'environmentComplete':True,'serverLogsCollected':True}}
        (p/'result.json').write_text(json.dumps(result))
        start={'event':'start','runId':'r','requestId':'a','at':1000}
        end={'event':'end','runId':'r','requestId':'a','startedAtMs':1000,'finishedAtMs':1020,'latencyMs':20,'outcome':outcome}
        (p/'client.log').write_text('\n'.join('TRACE_JSON '+json.dumps(x) for x in [start,end]))
        server={'runId':'r','requestId':'a','serverDurationMs':10,'queries':[],'sqlTruncated':False,'async':False}
        (p/'server.log').write_text(('EXPERIMENT_JSON '+json.dumps(server)+'\n')*(2 if duplicate else 1))
        (p/'summary.json').write_text(json.dumps({'metrics':{'http_reqs':{'values':{'count':1}}}}))
        return p

    def test_completed_capture_is_aggregated_and_passes_only_after_validation(self):
        with tempfile.TemporaryDirectory() as d:
            p=self.fixture(d)
            self.assertEqual(json.loads((p/'result.json').read_text())['status'],'collected')
            self.assertEqual(aggregate_run(p)['status'],'passed')
            self.assertTrue((p/'traces.jsonl').is_file())

    def test_timeout_and_collection_failure_remain_failures(self):
        for outcome,sample_ok in [('timeout',True),('success',False)]:
            with self.subTest(outcome=outcome,sample_ok=sample_ok), tempfile.TemporaryDirectory() as d:
                self.assertEqual(aggregate_run(self.fixture(d,outcome,sample_ok))['status'],'failed')

    def test_duplicate_server_event_still_fails(self):
        with tempfile.TemporaryDirectory() as d:
            result=aggregate_run(self.fixture(d,duplicate=True))
            self.assertEqual(result['status'],'failed')
            self.assertIn('Duplicate server event',result['stopReason'])
