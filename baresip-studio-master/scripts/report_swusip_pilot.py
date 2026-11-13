"""Collect pilot evidence without treating a build or synthetic lookup as a PBX call."""
from pathlib import Path
import datetime
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'docs/evidence'


def decoded(path):
    raw = path.read_bytes()
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert 'exit=0' in decoded(EVIDENCE / 'swusip-pilot-build.exit.txt'), 'Pilot build must pass first'
    log = decoded(EVIDENCE / 'swusip-pilot-build.log')
    assert 'BUILD SUCCESSFUL' in log
    target = EVIDENCE / 'swusip-pilot'
    target.mkdir(exist_ok=True)
    suites = []
    for path in sorted((ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        suites.append({key: suite.get(key) for key in ('name', 'tests', 'failures', 'errors', 'skipped')})
        assert int(suite.get('failures', 0)) == 0 and int(suite.get('errors', 0)) == 0
        shutil.copyfile(path, target / path.name)
    assert sum(int(s['tests']) for s in suites) >= 21
    python_tests = {}
    for script in ('test_source_integrity.py', 'test_device_privacy.py', 'test_translations.py'):
        result = subprocess.run(['python', str(ROOT / 'scripts' / script)], cwd=ROOT,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (target / (script.removesuffix('.py') + '.txt')).write_bytes(result.stdout)
        assert result.returncode == 0, script
        python_tests[script] = 'PASS'
    native = decoded(EVIDENCE / 'swusip-redesign-native.txt')
    assert len(re.findall(r'^PASS ', native, re.M)) == 15
    assert subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--', 'app/src/main/cpp',
                           'libbaresip-android', 'distribution'], cwd=ROOT).returncode == 0
    lint_path = ROOT / 'app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt'
    lint = decoded(lint_path)
    lint_counts = re.search(r'(\d+) errors?, (\d+) warnings?', lint)
    assert lint_counts and int(lint_counts[1]) == 0
    (target / 'lint-results.txt').write_text(lint, encoding='utf-8')
    app_gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    android_test_apk = ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    assert apk.is_file() and android_test_apk.is_file()
    artifacts = ROOT / 'artifacts'
    artifacts.mkdir(exist_ok=True)
    deliverable = artifacts / 'SWUSIP-1.0.0-pilot-debug.apk'
    shutil.copyfile(apk, deliverable)
    source = sorted(p for p in (ROOT / 'app/src').rglob('*') if p.is_file())
    source += [ROOT / p for p in ('app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts',
                                  'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties')]
    inventory = {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(source)}
    (target / 'source-sha256.json').write_text(json.dumps(inventory, indent=2) + '\n', encoding='utf-8')
    report = {
        'generatedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'package': 'ug.go.mwe.swusip',
        'versionCode': int(re.search(r'versionCode = (\d+)', app_gradle)[1]),
        'versionName': re.search(r'versionName = "([^"]+)"', app_gradle)[1],
        'build': 'PASS', 'lint': {'errors': int(lint_counts[1]), 'warnings': int(lint_counts[2])},
        'unitTestSuites': suites, 'pythonTests': python_tests,
        'nativeArchives': {'passed': 15, 'nativeSourceChanged': False},
        'apk': {'path': deliverable.relative_to(ROOT).as_posix(), 'bytes': apk.stat().st_size,
                'sha256': digest(apk), 'signing': 'DEBUG PILOT'},
        'androidDeviceTests': {'compiled': True, 'execution': 'DEFERRED_BY_USER'},
        'syntheticLookup': {'status': 'PASS', 'mechanism': 'CustomerMatching unit test; no SIP event simulated',
                            'customer': 'Synthetic Customer', 'account': '000123', 'scheme': 'Synthetic Water Scheme'},
        'deviceInstallation': 'DEFERRED_BY_USER_PHONE_POWERED_OFF',
        'deviceUiReview': 'DEFERRED_BY_USER',
        'authenticatedDirectoryDeviceFlow': 'NOT_TESTED',
        'liveSipRegistration': 'NOT_TESTED_NO_EXTENSION_PROVISIONED',
        'liveIncomingCallCustomerIdentification': 'NOT_TESTED',
        'twoWayPbxAudio': 'NOT_TESTED',
        'productionPbxChanges': False, 'productionServerChanges': False,
        'smsMmsBackendRemoval': 'PENDING_GENUINE_SIP_REGRESSION_PER_USER_REQUIREMENT',
        'productionInstitutionalSigningAuthority': 'NOT_CONFIGURED',
        'independentSecurityReview': 'PENDING_BEFORE_REAL_CUSTOMER_DATA',
        'fullAcceptance': 'NOT_YET_MET_DEVICE_AND_PBX_GATES_DEFERRED',
    }
    (target / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    # Preserve raw transcripts privately, normalise only completed evidence files.
    raw = ROOT / '.native-build/redesign-raw'
    raw.mkdir(exist_ok=True)
    for path in [*EVIDENCE.glob('swusip-*.log'), *EVIDENCE.glob('swusip-*.txt')]:
        shutil.copyfile(path, raw / path.name)
        value = decoded(path)
        path.write_text('\n'.join(line.rstrip() for line in value.splitlines()) + '\n', encoding='utf-8')
    print(json.dumps({'build': report['build'], 'unitTests': sum(int(s['tests']) for s in suites),
                      'lint': report['lint'], 'nativeArchives': 15, 'apkSha256': digest(apk),
                      'deviceAndPbx': 'DEFERRED'}, indent=2))


if __name__ == '__main__':
    main()
