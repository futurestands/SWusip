"""Protect app-scoped Logcat selection without needing or simulating a SIP engine."""
import unittest

from test_android_device import app_uid, scoped_log_lines, executable_mapping_in_range, service_startup_verified

class ScopedDeviceLogging(unittest.TestCase):
    def test_selects_only_exact_application_uid(self):
        output = ('package:ug.go.mwe.swusip.other uid:10001\n'
                  'package:ug.go.mwe.swusip uid:10002\n')
        self.assertEqual(app_uid(output), '10002')

    def test_does_not_capture_logs_for_similarly_named_app(self):
        self.assertIsNone(app_uid('package:ug.go.mwe.swusip.other uid:10001\n'))

    def test_padded_epoch_timestamps_are_not_silently_discarded(self):
        logs = '--------- beginning of main\n         1234.500 42 42 E AndroidRuntime: FATAL EXCEPTION\n'
        self.assertEqual(len(scoped_log_lines(logs, 1234)), 1)

    def test_old_crash_does_not_contaminate_current_observation(self):
        logs = ' 1000.000 42 42 E AndroidRuntime: FATAL EXCEPTION\n 2000.000 42 42 I App: current\n'
        self.assertEqual(scoped_log_lines(logs, 1500), [' 2000.000 42 42 I App: current'])

    def test_executable_mapping_must_match_exact_apk_and_native_byte_range(self):
        maps = ('1000-2000 r-xp 00008000 00:01 1 /data/app/ours/base.apk\n'
                '2000-3000 r--p 00009000 00:01 1 /data/app/ours/base.apk\n')
        self.assertTrue(executable_mapping_in_range(maps, {'/data/app/ours/base.apk'}, 0x8000, 0x1000))
        self.assertFalse(executable_mapping_in_range(maps, {'/data/app/other/base.apk'}, 0x8000, 0x1000))
        self.assertFalse(executable_mapping_in_range(maps, {'/data/app/ours/base.apk'}, 0x9000, 0x1000))

    def test_process_survival_alone_cannot_pass_service_startup(self):
        report = {'activityStarted': True, 'processAlive': True, 'nativeLibraryMapped': False,
                  'foregroundServiceObserved': True, 'logcatFailureCounts': {'crash': 0}}
        self.assertFalse(service_startup_verified(report))
        report['nativeLibraryMapped'] = True
        self.assertTrue(service_startup_verified(report))
        report['logcatFailureCounts']['crash'] = 1
        self.assertFalse(service_startup_verified(report))

if __name__ == '__main__':
    unittest.main(verbosity=2)
