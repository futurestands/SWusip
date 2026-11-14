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
import struct
import sys
import time
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKAGE = 'ug.go.mwe.swusip'

def app_uid(package_output):
    match = re.search(r'^package:' + re.escape(PACKAGE) + r'\s+uid:(\d+)\s*$', package_output, re.M)
    return match[1] if match else None

def scoped_log_lines(logs, start):
    # Android's epoch format pads timestamps with leading spaces on some devices.
    return [line for line in logs.splitlines()
            if (match := re.match(r'^\s*(\d+\.\d+)\s', line)) and float(match[1]) >= start]

def executable_mapping_in_range(maps, installed_paths, offset, size):
    for line in maps.splitlines():
        columns = line.split(maxsplit=5)
        if len(columns) == 6 and 'x' in columns[1] and columns[5] in installed_paths:
            if offset <= int(columns[2], 16) < offset + size:
                return True
    return False

def native_mapping_method(maps, apk, installed_paths):
    parents = [str(pathlib.PurePosixPath(path).parent) + '/' for path in installed_paths]
    for line in maps.splitlines():
        columns = line.split(maxsplit=5)
        if (len(columns) == 6 and 'x' in columns[1] and columns[5].endswith('/libbaresip.so')
                and any(columns[5].startswith(parent) for parent in parents)):
            return 'extracted-library'
    # Modern Android loads uncompressed native libraries directly from base.apk.
    # /proc/maps then names the APK, not libbaresip.so. Match its ZIP byte range
    # against an executable mapping of the exact installed, hash-verified APK.
    with zipfile.ZipFile(apk) as archive:
        entry = archive.getinfo('lib/arm64-v8a/libbaresip.so')
        if entry.compress_type != zipfile.ZIP_STORED:
            return None
        with apk.open('rb') as stream:
            stream.seek(entry.header_offset)
            header = stream.read(30)
        if header[:4] != b'PK\x03\x04':
            raise ValueError('Invalid APK local ZIP header')
        name_size, extra_size = struct.unpack_from('<HH', header, 26)
        offset = entry.header_offset + 30 + name_size + extra_size
        if executable_mapping_in_range(maps, installed_paths, offset, entry.file_size):
            return 'apk-embedded-executable-segment'
    return None

def service_startup_verified(report):
    return (report['activityStarted'] and report['processAlive']
            and report.get('nativeLibraryMapped') is True
            and report.get('foregroundServiceObserved') is True
            and not any(report['logcatFailureCounts'].values()))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=pathlib.Path, required=True)
    parser.add_argument('--serial', help='Required when more than one physical phone is present; never published')
    parser.add_argument('--seconds', type=int, choices=range(10, 61), default=30)
    parser.add_argument('--observe-only', action='store_true', help='Observe the installed APK without reinstalling it')
    parser.add_argument('--expected-apk-sha256', help='Require this verified local/installed APK digest')
    parser.add_argument('--report', type=pathlib.Path, default=ROOT / 'docs/evidence/localisation/device-startup.json')
    args = parser.parse_args()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    private = ROOT / '.native-build/device-tests' / stamp
    private.mkdir(parents=True, exist_ok=True)
    report_path = args.report
    report = {'timeUtc': stamp, 'status': 'blocked', 'gate2': 'NOT TESTED',
              'observationSeconds': args.seconds}

    def save():
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    def adb(*command, selected=False):
        invocation = [str(args.adb)]
        if selected or args.serial:
            invocation += ['-s', serial if selected else args.serial]
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
        if args.expected_apk_sha256 and report['apkSha256'] != args.expected_apk_sha256.lower():
            raise RuntimeError('Local APK does not match the expected verified build checksum')
        report['observeOnly'] = args.observe_only
        if not args.observe_only:
            code, output = adb('install', '-r', str(apk), selected=True)
            (private / 'install.txt').write_text(output, encoding='utf-8')
            report['installExit'] = code
            if code or 'Success' not in output:
                raise RuntimeError('Install failed; inspect private install.txt. Do not uninstall or clear data automatically.')
        code, paths = adb('shell', 'pm', 'path', PACKAGE, selected=True)
        installed_paths = {line.removeprefix('package:').strip() for line in paths.splitlines()
                           if line.startswith('package:')}
        bases = [path for path in installed_paths if path.endswith('/base.apk')]
        if code or len(bases) != 1:
            raise RuntimeError('Cannot identify the installed application base APK')
        code, digest = adb('shell', 'sha256sum', bases[0], selected=True)
        report['installedApkHashVerified'] = code == 0 and digest.split()[0] == report['apkSha256'] if digest.split() else False
        if not report['installedApkHashVerified']:
            raise RuntimeError('Installed APK checksum does not match the verified local APK')
        code, uid = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', PACKAGE, selected=True)
        application_uid = app_uid(uid)
        if code or not application_uid:
            raise RuntimeError('Cannot determine app UID for scoped Logcat capture')
        code, device_time = adb('shell', 'date', '+%s', selected=True)
        if code:
            raise RuntimeError('Cannot establish device-clock Logcat boundary')
        start = float(device_time.strip())
        report['deviceLogStartEpoch'] = start
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
        lines = scoped_log_lines(logs, start)
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
            (private / 'app-maps.txt').write_text(maps, encoding='utf-8')
            method = native_mapping_method(maps, apk, installed_paths) if map_code == 0 else None
            report['nativeLibraryMapped'] = method is not None if map_code == 0 else None
            report['nativeMappingMethod'] = method
        service_code, services = adb('shell', 'dumpsys', 'activity', 'services', PACKAGE, selected=True)
        (private / 'app-services.txt').write_text(services, encoding='utf-8')
        report['baresipServiceObserved'] = 'com.tutpro.baresip.BaresipService' in services if service_code == 0 else None
        report['foregroundServiceObserved'] = (report['baresipServiceObserved'] and 'isForeground=true' in services) if service_code == 0 else None
        passed = service_startup_verified(report)
        report['status'] = 'ui_native_and_foreground_startup_verified' if passed else 'startup_failed_or_incomplete'
        report['limits'] = 'Observation window only. No SIP registration, two-way audio or long-term reliability test performed.'
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
