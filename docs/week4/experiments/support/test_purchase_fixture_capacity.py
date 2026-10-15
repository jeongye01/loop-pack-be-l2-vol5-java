"""Ensure increased arrival rates get sufficient fresh buyers without changing the rate."""
import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import mixed_purchase_fixture as fixture

class FixtureCapacityTest(unittest.TestCase):
    def test_twenty_rps_is_preserved_and_has_more_than_1200_buyers(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'config.json';p.write_text(json.dumps({'durationSeconds':60,'purchase':{'rps':20,'vus':100}}))
            queries=[]
            def sql(config,key,query):
                queries.append(query)
                return [[str(i),'100'] for i in range(1,21)] if query.startswith('SELECT') else [['1000']]
            with patch.object(sys,'argv',['fixture','--config',str(p),'--ssh-key','unused','prepare']),patch.object(fixture,'sql',side_effect=sql),contextlib.redirect_stdout(io.StringIO()): fixture.main()
            c=json.loads(p.read_text());self.assertEqual(c['purchase']['rps'],20)
            self.assertGreater(len(c['purchase']['buyers']),1200)
            self.assertEqual(len(set(c['purchase']['buyers'])),len(c['purchase']['buyers']))
            self.assertIn('@first+1249',queries[-1])

if __name__=='__main__': unittest.main()
