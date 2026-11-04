import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'virustotal_scan.py'


class VirusTotalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.apk = self.root / 'app.apk'
        self.alias = self.root / 'alias.apk'
        self.apk.write_bytes(b'apk bytes')
        self.alias.write_bytes(b'apk bytes')
        self.digest = hashlib.sha256(b'apk bytes').hexdigest()
        self.output = self.root / 'out'

    def tearDown(self):
        self.temp.cleanup()

    def cli(self, *extra, key=None):
        env = dict(os.environ)
        env.pop('VIRUSTOTAL_API_KEY', None)
        if key is not None:
            env['VIRUSTOTAL_API_KEY'] = key
        return subprocess.run([sys.executable, str(SCRIPT), '--apk', str(self.apk),
            '--alias-apk', str(self.alias), '--version', 'v0.6.2',
            '--output-dir', str(self.output), *extra], env=env, capture_output=True, text=True)

    def artifact(self, name):
        return json.loads((self.output / name).read_text(encoding='utf-8'))

    def test_prepare_binds_pending_badge_and_report_to_version_and_hash(self):
        result = self.cli('--prepare-only')
        self.assertEqual(result.returncode, 0, result.stderr)
        report = self.artifact('virustotal-report.json')
        badge = self.artifact('virustotal-badge.json')
        self.assertEqual((report['version'], report['sha256'], report['status']), ('v0.6.2', self.digest, 'pending'))
        self.assertEqual(badge['schemaVersion'], 1)
        self.assertIn('v0.6.2', badge['message'])
        self.assertNotEqual(badge['color'], 'green')
        notes = (self.output / 'virustotal-notes.md').read_text(encoding='utf-8')
        self.assertIn('<!-- wystore-virustotal:start -->', notes)
        self.assertIn('https://img.shields.io/static/v1?', notes)
        self.assertIn('v0.6.2', notes)
        self.assertIn(self.digest, notes)

    def test_alias_mismatch_fails_before_outputs(self):
        self.alias.write_bytes(b'different')
        result = self.cli('--prepare-only')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.output / 'virustotal-report.json').exists())

    def test_missing_key_writes_error_artifacts(self):
        result = self.cli()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.artifact('virustotal-report.json')['status'], 'error')
        self.assertNotEqual(self.artifact('virustotal-badge.json')['color'], 'green')


class ApiTests(unittest.TestCase):
    def setUp(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('vt', SCRIPT)
        self.vt = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.vt)
        self.temp = tempfile.TemporaryDirectory()
        self.apk = Path(self.temp.name) / 'app.apk'
        self.apk.write_bytes(b'apk bytes')
        self.digest = hashlib.sha256(b'apk bytes').hexdigest()
        self.current = 0
        self.calls = []
        self.responses = []

    def tearDown(self):
        self.temp.cleanup()

    def sleep(self, seconds):
        self.current += seconds

    def send(self, method, url, headers, body, timeout):
        self.calls.append((self.current, method, url, headers, body))
        return self.responses.pop(0)

    def client(self):
        return self.vt.Client('secret-key', self.send, lambda: self.current, self.sleep)

    def reply(self, value, status=200, headers=None):
        self.responses.append((status, headers or {}, json.dumps(value).encode()))

    def analysis(self, status='completed', stats=None, digest=None):
        return {'data': {'id': 'analysis-id', 'attributes': {'status': status,
            'date': 1700000000, 'stats': stats if stats is not None else
            {'malicious': 0, 'suspicious': 0, 'harmless': 3, 'undetected': 7,
             'failure': 10, 'timeout': 2, 'type-unsupported': 5}}},
            'meta': {'file_info': {'sha256': digest or self.digest}}}

    def file(self, digest=None, size=9):
        return {'data': {'id': digest or self.digest, 'attributes': {'sha256': digest or self.digest, 'size': size}}}

    def test_completed_uses_analysis_counts_and_excludes_nonjudgments(self):
        self.reply({'data': {'id': 'analysis-id'}})
        self.reply(self.analysis('queued'))
        self.reply(self.analysis())
        self.reply(self.file())
        result = self.vt.scan(self.client(), self.apk, self.digest, 9)
        self.assertEqual((result['status'], result['analysed']), ('completed', 10))
        self.assertEqual([call[0] for call in self.calls], [0, 16, 32, 48])
        self.assertEqual(self.calls[0][1:3], ('POST', self.vt.API + '/files'))

    def test_large_upload_uses_trusted_upload_url(self):
        self.reply({'data': 'https://www.virustotal.com/api/v3/files/upload?token=private'})
        self.reply({'data': {'id': 'analysis-id'}})
        self.reply(self.analysis())
        self.reply(self.file(size=self.vt.SMALL_SIZE + 1))
        self.vt.scan(self.client(), self.apk, self.digest, self.vt.SMALL_SIZE + 1)
        self.assertTrue(self.calls[0][2].endswith('/files/upload_url'))
        self.assertEqual(self.calls[1][1], 'POST')

    def test_large_upload_uses_the_official_bigfiles_host(self):
        url = 'https://bigfiles.virustotal.com/_ah/upload/one-use-token'
        self.reply({'data': url})
        self.reply({'data': {'id': 'analysis-id'}})
        self.reply(self.analysis())
        self.reply(self.file(size=self.vt.SMALL_SIZE + 1))
        result = self.vt.scan(self.client(), self.apk, self.digest, self.vt.SMALL_SIZE + 1)
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(self.calls[1][1:3], ('POST', url))

    def test_untrusted_upload_url_never_receives_key(self):
        self.reply({'data': 'https://evil.example/upload'})
        with self.assertRaises(self.vt.ScanError):
            self.vt.scan(self.client(), self.apk, self.digest, self.vt.SMALL_SIZE + 1)
        self.assertEqual(len(self.calls), 1)

    def test_queued_response_times_out_without_green_result(self):
        self.reply({'data': {'id': 'analysis-id'}})
        for _ in range(100):
            self.reply(self.analysis('queued'))
        with self.assertRaises(self.vt.ScanTimeout):
            self.vt.scan(self.client(), self.apk, self.digest, 9)

    def test_bad_stats_and_hash_mismatch_are_rejected(self):
        for response in [self.analysis(stats={'malicious': False, 'suspicious': 0, 'harmless': 0, 'undetected': 0}),
                         self.analysis(stats={'malicious': 0, 'suspicious': 0, 'harmless': 0, 'undetected': 0}),
                         self.analysis(digest='bad')]:
            with self.subTest(response=response):
                self.responses = []
                self.reply({'data': {'id': 'analysis-id'}})
                self.reply(response)
                with self.assertRaises(self.vt.ScanError):
                    self.vt.scan(self.client(), self.apk, self.digest, 9)

    def test_mismatched_file_identity_is_rejected(self):
        self.reply({'data': {'id': 'analysis-id'}})
        self.reply(self.analysis())
        self.reply(self.file(size=8))
        with self.assertRaises(self.vt.ScanError):
            self.vt.scan(self.client(), self.apk, self.digest, 9)

    def test_rate_limit_honors_retry_after_and_auth_hides_key(self):
        self.reply({}, status=429, headers={'Retry-After': '40'})
        self.reply({}, status=403)
        with self.assertRaises(self.vt.ScanError) as error:
            self.client().request('GET', self.vt.API + '/files/example')
        self.assertNotIn('secret-key', str(error.exception))
        self.assertEqual([call[0] for call in self.calls], [0, 40])

    def test_upload_is_never_retried_on_rate_limit(self):
        self.reply({}, status=429)
        with self.assertRaises(self.vt.ScanError):
            self.vt.scan(self.client(), self.apk, self.digest, 9)
        self.assertEqual(len(self.calls), 1)

class ArtifactTests(unittest.TestCase):
    def test_badge_colors_distinguish_detection_and_incomplete_results(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('vt_artifacts', SCRIPT)
        vt = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vt)
        with tempfile.TemporaryDirectory() as tmp:
            report = {'version': 'v0.6.2', 'sha256': 'a' * 64, 'size': 1,
                'checkedAt': '2026-01-01T00:00:00Z', 'report_url': 'https://www.virustotal.com/gui/file/' + 'a' * 64,
                'analysisDate': None, 'analysisId': None, 'malicious': 0, 'suspicious': 0, 'analysed': 3}
            cases = [('completed', 0, 0, 'green'), ('completed', 0, 1, 'orange'),
                     ('completed', 1, 0, 'red'), ('pending', 0, 0, 'yellow'),
                     ('timeout', 0, 0, 'lightgrey'), ('error', 0, 0, 'lightgrey')]
            for status, malicious, suspicious, color in cases:
                with self.subTest(status=status, malicious=malicious, suspicious=suspicious):
                    report.update(status=status, malicious=malicious, suspicious=suspicious)
                    vt.write_assets(Path(tmp), report)
                    badge = json.loads((Path(tmp) / 'virustotal-badge.json').read_text())
                    self.assertEqual(badge['color'], color)
                    self.assertIn('v0.6.2', badge['message'])
                    if status != 'completed':
                        self.assertNotIn('0/0', badge['message'])

class RetryTests(unittest.TestCase):
    def test_invalid_retry_after_falls_back_to_rate_limit_interval(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('vt_retry', SCRIPT)
        vt = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vt)
        times = []
        current = [0]
        def send(*args):
            times.append(current[0])
            return (429, {'Retry-After': 'nan'}, b'') if len(times) == 1 else (200, {}, b'{}')
        def sleep(seconds):
            self.assertTrue(seconds >= 0 and seconds < 100)
            current[0] += seconds
        vt.Client('secret', send, lambda: current[0], sleep).request('GET', vt.API + '/test')
        self.assertEqual(times, [0, 16])

class MainIntegrationTests(unittest.TestCase):
    def test_completed_detection_writes_report_and_succeeds(self):
        import importlib.util
        from unittest.mock import patch
        spec = importlib.util.spec_from_file_location('vt_main', SCRIPT)
        vt = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vt)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk, alias = root / 'app.apk', root / 'alias.apk'
            apk.write_bytes(b'apk bytes')
            alias.write_bytes(b'apk bytes')
            digest = hashlib.sha256(b'apk bytes').hexdigest()
            response = {'analysisId': 'id', 'analysisDate': '2026-01-01T00:00:00Z',
                        'status': 'completed', 'malicious': 2, 'suspicious': 1, 'analysed': 60}
            args = ['--apk', str(apk), '--alias-apk', str(alias), '--version', 'v0.6.2', '--output-dir', str(root / 'out')]
            with patch.dict(os.environ, {'VIRUSTOTAL_API_KEY': 'secret-key'}), patch.object(vt, 'scan', return_value=response):
                self.assertEqual(vt.main(args), 0)
            report = json.loads((root / 'out' / 'virustotal-report.json').read_text())
            badge = json.loads((root / 'out' / 'virustotal-badge.json').read_text())
            self.assertEqual((report['sha256'], report['size'], report['malicious']), (digest, 9, 2))
            self.assertEqual(badge['color'], 'red')
            self.assertNotIn('secret-key', json.dumps(report) + json.dumps(badge))

class UrlTests(unittest.TestCase):
    def test_bigfiles_is_only_trusted_for_uploads_and_spoofed_origins_are_rejected(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('vt_upload_url', SCRIPT)
        vt = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vt)
        self.assertTrue(vt.trusted_url('https://bigfiles.virustotal.com/_ah/upload/token', upload=True))
        self.assertFalse(vt.trusted_url('https://bigfiles.virustotal.com/_ah/upload/token'))
        for url in ('http://bigfiles.virustotal.com/upload',
                    'https://bigfiles.virustotal.com.evil.example/upload',
                    'https://sub.bigfiles.virustotal.com/upload',
                    'https://evil.example/?host=bigfiles.virustotal.com',
                    'https://user:secret@bigfiles.virustotal.com/upload',
                    'https://bigfiles.virustotal.com:444/upload',
                    'https://bigfiles.virustotal.com/upload#fragment'):
            with self.subTest(url=url):
                self.assertFalse(vt.trusted_url(url, upload=True))

    def test_invalid_port_is_rejected_without_raising_or_exposing_url(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('vt_url', SCRIPT)
        vt = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vt)
        self.assertFalse(vt.trusted_url('https://www.virustotal.com:secret/upload'))


if __name__ == '__main__':
    unittest.main()
