"""Regression tests against the actual historical fake-engine incident."""
import pathlib
import subprocess
import unittest

from check_sip_source import check_source

ROOT = pathlib.Path(__file__).resolve().parents[1]

class NativeSourceRegression(unittest.TestCase):
    def test_restored_engine_passes(self):
        source = (ROOT / 'app/src/main/cpp/baresip.c').read_text(encoding='utf-8')
        errors, count = check_source(source)
        self.assertEqual(errors, [])
        self.assertGreaterEqual(count, 90)

    def test_actual_historical_fake_engine_is_rejected(self):
        prefix = subprocess.check_output(['git', 'rev-parse', '--show-prefix'], cwd=ROOT, text=True).strip()
        # Read history as data. Never write, compile or install the fake engine.
        source = subprocess.check_output(['git', 'show',
            'c336f73:' + prefix + 'app/src/main/cpp/baresip.c'], cwd=ROOT, text=True)
        errors, count = check_source(source)
        self.assertTrue(errors, 'The known nonfunctional engine must fail the guard')
        self.assertLess(count, 90)
        self.assertTrue(any('call_connect' in error for error in errors))

    def test_task_receiver_unexported(self):
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
        self.assertIn('.TaskReceiver', manifest)
        self.assertNotIn('tools:ignore="ExportedReceiver"', manifest)
        # Verify TaskReceiver block has android:exported="false"
        import xml.etree.ElementTree as ET
        tree = ET.fromstring(manifest)
        ns = {'android': 'http://schemas.android.com/apk/res/android'}
        app = tree.find('application')
        receivers = app.findall('receiver')
        task_receiver = next((r for r in receivers if r.attrib.get(f'{{{ns["android"]}}}name') in ('.TaskReceiver', 'com.tutpro.baresip.TaskReceiver')), None)
        self.assertIsNotNone(task_receiver, 'TaskReceiver must be present')
        self.assertEqual(task_receiver.attrib.get(f'{{{ns["android"]}}}exported'), 'false', 'SEC-01: TaskReceiver must not be exported')

    def test_file_paths_root_restricted(self):
        paths_xml = (ROOT / 'app/src/main/res/xml/file_paths.xml').read_text(encoding='utf-8')
        import xml.etree.ElementTree as ET
        tree = ET.fromstring(paths_xml)
        for elem in tree.findall('files-path'):
            path = elem.attrib.get('path', '')
            self.assertNotEqual(path, '.', 'SEC-03: file_paths must not expose root filesDir with path="."')
            self.assertNotEqual(path, '', 'SEC-03: file_paths must not expose root filesDir with empty path')


if __name__ == '__main__':
    unittest.main(verbosity=2)
