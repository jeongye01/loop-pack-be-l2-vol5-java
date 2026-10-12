import unittest
from report import aggregate, distribution

class ReportTest(unittest.TestCase):
    def test_percentiles_and_missing_are_not_zero(self):
        self.assertEqual(distribution(list(range(1,101)))['p95'],95)
        self.assertIsNone(distribution([])['mean'])

    def test_timeout_still_joins_late_server_completion(self):
        config={'runId':'r','rps':1,'durationSeconds':2}
        client=[{'event':'start','runId':'r','requestId':'a','at':1000},
                {'event':'end','runId':'r','requestId':'a','startedAtMs':1000,'finishedAtMs':6000,
                 'latencyMs':5000,'outcome':'timeout'}]
        server=[{'runId':'r','requestId':'a','serverDurationMs':8000,
                 'queries':[{'durationMs':7000}], 'sqlTruncated':False,'async':False}]
        traces,result=aggregate(config,client,server,{'metrics':{'http_reqs':{'values':{'count':1}}}})
        self.assertEqual(traces[0]['serverDurationMs'],8000)
        self.assertEqual(result['metrics']['successRps'],0)
        self.assertEqual(result['metrics']['completedRps'],.2)
        self.assertEqual(result['requests']['byOutcome'],{'timeout':1})

    def test_missing_end_or_server_is_incomplete_not_success(self):
        traces,result=aggregate({'runId':'r','rps':1,'durationSeconds':2},
            [{'event':'start','runId':'r','requestId':'a','at':1000}],[],None)
        self.assertIsNone(traces[0]['serverDurationMs'])
        self.assertIsNone(result['metrics']['completedRps'])
        self.assertIsNone(result['requests']['dropped'])
        self.assertFalse(result['validation']['measurementComplete'])

    def test_other_runs_are_excluded_and_duplicates_rejected(self):
        config={'runId':'r','rps':1,'durationSeconds':2}
        start={'event':'start','runId':'r','requestId':'a','at':0}
        with self.assertRaises(ValueError): aggregate(config,[start,start],[],None)
        traces,_=aggregate(config,[dict(start,runId='other')],[],None)
        self.assertEqual(traces,[])

if __name__=='__main__': unittest.main()
