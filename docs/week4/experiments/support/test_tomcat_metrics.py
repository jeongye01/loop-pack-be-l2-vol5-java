import unittest
from tomcat_metrics import parse, summarize

class TomcatMetricsTest(unittest.TestCase):
    def test_connectors_are_not_added_together(self):
        text = ('tomcat_threads_busy_threads{application="commerce-api",name="http-nio-0.0.0.0-8080"} 200.0\n'
                'tomcat_threads_busy_threads{name="http-nio-0.0.0.0-8081",application="commerce-api"} 1.0')
        values = parse(text)
        self.assertEqual(values['http-nio-0.0.0.0-8080']['tomcat_threads_busy_threads'],200)
        self.assertEqual(values['http-nio-0.0.0.0-8081']['tomcat_threads_busy_threads'],1)
    def test_missing_is_not_zero(self):
        self.assertEqual(parse('# no Tomcat metrics'),{})
        result=summarize([{'app':{'stdout':'tomcat_threads_busy_threads{name="app"} 4'}}, {'app':{'stdout':''}}])
        self.assertEqual(result['app']['tomcat_threads_busy_threads']['count'],1)
        self.assertEqual(result['app']['tomcat_threads_busy_threads']['mean'],4)
