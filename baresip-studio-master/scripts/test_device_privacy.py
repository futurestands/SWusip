"""Protect app-scoped Logcat selection without needing or simulating a SIP engine."""
import unittest

from test_android_device import app_uid

class ScopedDeviceLogging(unittest.TestCase):
    def test_selects_only_exact_application_uid(self):
        output = ('package:ug.go.mwe.swusip.other uid:10001\n'
                  'package:ug.go.mwe.swusip uid:10002\n')
        self.assertEqual(app_uid(output), '10002')

    def test_does_not_capture_logs_for_similarly_named_app(self):
        self.assertIsNone(app_uid('package:ug.go.mwe.swusip.other uid:10001\n'))

if __name__ == '__main__':
    unittest.main(verbosity=2)
