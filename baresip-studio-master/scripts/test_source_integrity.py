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

if __name__ == '__main__':
    unittest.main(verbosity=2)
