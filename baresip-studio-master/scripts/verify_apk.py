"""Inspect debug APK identity, genuine JNI symbols, ABI, and ELF dependencies."""
import argparse
import hashlib
import pathlib
import re
import subprocess
import sys
import zipfile

def run(*args):
    return subprocess.check_output(list(map(str, args)), text=True, stderr=subprocess.STDOUT)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=pathlib.Path)
    parser.add_argument('--aapt', type=pathlib.Path, required=True)
    parser.add_argument('--tools', type=pathlib.Path, required=True)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    badging = run(args.aapt, 'dump', 'badging', args.apk)
    if "package: name='ug.go.mwe.swusip'" not in badging:
        raise SystemExit('Wrong application ID')
    if "application-label:'SWUSIP'" not in badging:
        raise SystemExit('Wrong institutional display name')
    print(badging)
    suffix = '.exe' if sys.platform == 'win32' else ''
    with zipfile.ZipFile(args.apk) as apk:
        native = sorted(n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so'))
        print('Packaged native libraries:', *native, sep='\n')
        if not native or any(not n.startswith('lib/arm64-v8a/') for n in native):
            raise SystemExit('Expected exclusively arm64-v8a native libraries')
        entry = 'lib/arm64-v8a/libbaresip.so'
        if entry not in native:
            raise SystemExit('Missing native SIP library')
        destination = root / '.native-build/apk-inspection/libbaresip.so'
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(apk.read(entry))
    elf = run(args.tools / ('llvm-readelf' + suffix), '-h', '-d', destination)
    if 'AArch64' not in elf:
        raise SystemExit('Wrong ELF machine')
    print(elf)
    # AGP normally strips local symbols from APK binaries, so check dynamic JNI
    # exports as well; core static symbols are verified before stripping.
    dynamic = run(args.tools / ('llvm-nm' + suffix), '-D', '--defined-only', destination)
    dependencies = re.findall(r'\(NEEDED\).*?\[(.*?)\]', elf)
    platform_libraries = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libaaudio.so', 'libz.so'}
    for dependency in dependencies:
        if dependency not in platform_libraries and 'lib/arm64-v8a/' + dependency not in native:
            raise SystemExit('Unpackaged native dependency: ' + dependency)
    for operation in ['Api_ua_1register', 'Api_call_1connect', 'Api_call_1transfer',
                      'BaresipService_baresipStart']:
        name = 'Java_com_tutpro_baresip_' + operation
        if name not in dynamic:
            raise SystemExit('Missing JNI implementation: ' + name)
    print('PASS APK identity, arm64 ELF and required dynamic JNI exports')
    print('APK SHA256:', hashlib.sha256(args.apk.read_bytes()).hexdigest())
    print('SIP interoperability and two-way audio remain separate acceptance tests.')
