import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
from remote_runner import RemoteRunner


class RemoteRunnerTest(unittest.TestCase):
    def runner(self):
        runner = object.__new__(RemoteRunner)
        runner.base = {'baseUrl':'http://public:8080','loadBaseUrl':'http://private:8080','sshUser':'ubuntu'}
        runner.load_host = 'load-host'
        runner.key = '/local/key.pem'
        runner.ssh = Mock(return_value={'exitCode':0})
        runner.copy = Mock()
        return runner

    @patch('remote_runner.subprocess.Popen')
    def test_remote_target_preserves_original_config_and_copies_no_credentials(self, popen):
        runner = self.runner()
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            config = {'url':'http://public:8080/api/v1/products?sort=latest', 'durationSeconds':60,
                      'gracefulStopSeconds':10,'vus':3000,'rps':1700}
            (out/'config.json').write_text(json.dumps(config))
            runner.launch_load(out, None)
            remote = json.loads((out/'load-config.json').read_text())
            self.assertEqual(remote['url'],'http://private:8080/api/v1/products?sort=latest')
            self.assertEqual(remote['vus'],3000)
            self.assertEqual(json.loads((out/'config.json').read_text()),config)
            self.assertNotIn(runner.key, runner.copy.call_args.args[0])
            script = popen.call_args.args[0][-1]
            self.assertIn('timeout --signal=TERM --kill-after=5s 90s',script)
            self.assertIn('--console-output client.log products.js',script)

    @patch('remote_runner.command', return_value={'exitCode':1,'stderr':'missing artifact'})
    def test_failed_transfer_is_not_accepted(self, command):
        runner = self.runner()
        with self.assertRaisesRegex(RuntimeError,'transfer failed'):
            RemoteRunner.copy(runner,['missing'],'destination')


if __name__ == '__main__':
    unittest.main()
