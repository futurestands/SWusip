"""Install/launch SWUSIP on one authorised physical phone; keep raw logs private.

Does not clear app data, uninstall, grant permissions, change PBX configuration,
start external calls, or enable network debugging. Reinstall preserves existing data.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKAGE = 'ug.go.mwe.swusip'

def app_uid(package_output):
    match = re.search(r'^package:' + re.escape(PACKAGE) + r'\s+uid:(\d+)\s*$', package_output, re.M)
    return match[1] if match else None

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=pathlib.Path, required=True)
    parser.add_argument('--serial', help='Required when more than one physical phone is present; never published')
    parser.add_argument('--seconds', type=int, choices=range(10, 61), default=30)
    args = parser.parse_args()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    private = ROOT / '.native-build/device-tests' / stamp
    private.mkdir(parents=True, exist_ok=True)
    report_path = ROOT / 'docs/evidence/localisation/device-startup.json'
    report = {'timeUtc': stamp, 'status': 'blocked', 'gate2': 'NOT TESTED',
              'observationSeconds': args.seconds}

    def save():
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    def adb(*command, selected=False):
        invocation = [str(args.adb)]
        if selected:
            invocation += ['-s', serial]
        result = subprocess.run(invocation + list(command), capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=120)
        return result.returncode, result.stdout + result.stderr

    try:
        status, devices = adb('devices', '-l')
        report['adbEnumerationExit'] = status
        (private / 'adb-devices.txt').write_text(devices, encoding='utf-8')
        if status:
            raise RuntimeError('ADB enumeration failed')
        entries = [line.split() for line in devices.splitlines()[1:] if line.strip()]
        physical = [entry for entry in entries if len(entry) > 1 and not entry[0].startswith('emulator-')]
        if args.serial:
            physical = [entry for entry in physical if entry[0] == args.serial]
        if not physical:
            report['blocker'] = 'No physical Android phone detected by ADB. Connect USB data cable and enable USB debugging.'
            save()
            print(report['blocker'])
            return 2
        if len(physical) != 1:
            report['blocker'] = 'Multiple physical devices: select the authorised phone using --serial.'
            save()
            print(report['blocker'])
            return 2
        serial, state = physical[0][:2]
        if state != 'device':
            report['blocker'] = 'ADB state is ' + state + '; unlock phone and accept this computer RSA debugging prompt.'
            save()
            print(report['blocker'])
            return 2
        for label, prop in [('model', 'ro.product.model'), ('androidVersion', 'ro.build.version.release'),
                            ('api', 'ro.build.version.sdk'), ('abis', 'ro.product.cpu.abilist')]:
            code, value = adb('shell', 'getprop', prop, selected=True)
            if code:
                raise RuntimeError('Device capability query failed')
            report[label] = value.strip()
        if 'arm64-v8a' not in report['abis'].split(',') or int(report['api']) < 28:
            report['blocker'] = 'Phone requires Android API 28+ and arm64-v8a support for this APK.'
            save()
            return 2
        apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
        report['apkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
        code, output = adb('install', '-r', str(apk), selected=True)
        (private / 'install.txt').write_text(output, encoding='utf-8')
        report['installExit'] = code
        if code or 'Success' not in output:
            raise RuntimeError('Install failed; inspect private install.txt. Do not uninstall or clear data automatically.')
        code, uid = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', PACKAGE, selected=True)
        application_uid = app_uid(uid)
        if code or not application_uid:
            raise RuntimeError('Cannot determine app UID for scoped Logcat capture')
        code, device_time = adb('shell', 'date', '+%s', selected=True)
        if code:
            raise RuntimeError('Cannot establish device-clock Logcat boundary')
        start = float(device_time.strip())
        code, launch = adb('shell', 'am', 'start', '-W', '-n',
                           PACKAGE + '/com.tutpro.baresip.MainActivity', selected=True)
        (private / 'launch.txt').write_text(launch, encoding='utf-8')
        report['launchExit'] = code
        report['activityStarted'] = code == 0 and 'Status: ok' in launch
        print('Observing app startup; grant requested microphone/notification permissions on the phone if appropriate.', flush=True)
        time.sleep(args.seconds)
        code, logs = adb('logcat', '-d', '-v', 'epoch', '--uid=' + application_uid, selected=True)
        if code:
            raise RuntimeError('UID-scoped Logcat unavailable; no startup pass can be claimed')
        lines = [line for line in logs.splitlines()
                 if re.match(r'^\d+\.\d+', line) and float(line.split()[0]) >= start]
        (private / 'app-logcat.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        checks = {
            'crash': r'FATAL EXCEPTION|Fatal signal|\bANR\b',
            'nativeLoading': r'UnsatisfiedLinkError|dlopen failed|cannot locate symbol',
            'foregroundService': (r'ForegroundServiceStartNotAllowedException|ForegroundServiceDidNotStartInTimeException|'
                                  r'MissingForegroundServiceTypeException|InvalidForegroundServiceTypeException|'
                                  r'RemoteServiceException|Bad notification for startForeground|startForegroundService.*did not'),
            'securityException': r'\bSecurityException\b',
        }
        report['logcatFailureCounts'] = {name: sum(bool(re.search(pattern, line, re.I)) for line in lines)
                                        for name, pattern in checks.items()}
        report['scopedLogcatLines'] = len(lines)
        code, pid = adb('shell', 'pidof', '-s', PACKAGE, selected=True)
        report['processAlive'] = code == 0 and pid.strip().isdigit()
        if report['processAlive']:
            map_code, maps = adb('shell', 'run-as', PACKAGE, 'cat', '/proc/' + pid.strip() + '/maps', selected=True)
            report['nativeLibraryMapped'] = 'libbaresip.so' in maps if map_code == 0 else None
        service_code, services = adb('shell', 'dumpsys', 'activity', 'services', PACKAGE, selected=True)
        (private / 'app-services.txt').write_text(services, encoding='utf-8')
        report['foregroundServiceObserved'] = 'isForeground=true' in services if service_code == 0 else None
        passed = report['activityStarted'] and report['processAlive'] and not any(report['logcatFailureCounts'].values())
        report['status'] = 'startup_observed_without_detected_failure' if passed else 'startup_failed_or_incomplete'
        report['limits'] = 'Observation window only; native/foreground flags require review. No SIP registration or audio test performed.'
        save()
        print(json.dumps(report, indent=2))
        return 0 if passed else 1
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as exc:
        report['status'] = 'failed_or_blocked'
        # Timeout exceptions include the command line/device serial; keep those
        # identifiers out of the public report as well as raw Logcat.
        report['blocker'] = ('ADB subprocess failed or timed out; inspect private test evidence.'
                             if isinstance(exc, subprocess.SubprocessError) else str(exc))
        save()
        print(report['blocker'], file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
