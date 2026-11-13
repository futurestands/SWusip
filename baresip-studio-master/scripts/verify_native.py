"""Fail closed on absent/empty archives, wrong ABI, and absent engine symbols."""
import argparse
import hashlib
import pathlib
import re
import subprocess
import sys
import tempfile

LIBRARIES = {
    'openssl': ['crypto', 'ssl'], 're': ['re'], 'baresip': ['baresip'],
    'opus': ['opus'], 'g722': ['g722'], 'g7221': ['g722_1'],
    'g729': ['bcg729'], 'ilbc': ['ilbc'], 'codec2': ['codec2'],
    'amr': ['amrnb', 'amrwb', 'amrwbenc'], 'gzrtp': ['zrtpcppcore'],
    'sndfile': ['sndfile'],
}
REQUIRED = {'re': ['libre_init'], 'baresip': [
    'baresip_init', 'ua_register', 'ua_init', 'call_connect', 'call_transfer']}

def run(tool, *args):
    return subprocess.check_output([str(tool), *map(str, args)], text=True,
                                   stderr=subprocess.STDOUT)

def check_abi(archive, members, tools, suffix, machine, abi):
    headers = run(tools / ('llvm-objdump' + suffix), '-f', archive)
    formats = re.findall(r'file format (\S+)', headers)
    if any(machine not in fmt for fmt in formats):
        raise ValueError('wrong object ABI: ' + str(set(formats)))
    if len(formats) == len(members):
        return
    # Genuine G.722 enables IPO: LLVM bitcode has no ELF header until LTO link.
    # Inspect every member's target triple instead of treating it as an empty .a.
    if len(set(members)) != len(members):
        raise ValueError('duplicate archive members prevent unambiguous LTO inspection')
    target = {'arm64-v8a': 'aarch64', 'armeabi-v7a': 'arm', 'x86_64': 'x86_64'}[abi]
    with tempfile.TemporaryDirectory(prefix='swusip-abi-') as temp:
        for member in members:
            data = subprocess.check_output([str(tools / ('llvm-ar' + suffix)), 'p', str(archive), member])
            obj = pathlib.Path(temp) / 'member.o'
            obj.write_bytes(data)
            if data.startswith((b'BC\xc0\xde', b'\xde\xc0\x17\x0b')):
                ir = run(tools / ('llvm-dis' + suffix), obj, '-o', '-')
                triple = re.search(r'^target triple = "([^"]+)"', ir, re.M)
                if not triple or not triple[1].startswith(target) or 'android' not in triple[1]:
                    raise ValueError('incorrect LTO target triple in ' + member)
            else:
                fmt = re.findall(r'file format (\S+)', run(tools / ('llvm-objdump' + suffix), '-f', obj))
                if not fmt or any(machine not in f for f in fmt):
                    raise ValueError('unrecognised or wrong ABI in ' + member)

def verify(root, abi, tools):
    machine = {'arm64-v8a': 'aarch64', 'armeabi-v7a': 'arm', 'x86_64': 'x86-64'}[abi]
    suffix = '.exe' if sys.platform == 'win32' else ''
    errors = []
    for dep, names in LIBRARIES.items():
        for name in names:
            archive = root / dep / 'lib' / abi / ('lib' + name + '.a')
            try:
                if not archive.is_file() or archive.stat().st_size <= 8:
                    raise ValueError('missing or empty archive')
                members = run(tools / ('llvm-ar' + suffix), 't', archive).splitlines()
                if not members:
                    raise ValueError('archive has no object members')
                check_abi(archive, members, tools, suffix, machine, abi)
                symbols = run(tools / ('llvm-nm' + suffix), '--defined-only', archive)
                for symbol in REQUIRED.get(name, []):
                    if not re.search(r'\b[Tt]\s+' + symbol + r'$', symbols, re.M):
                        raise ValueError('missing implementation: ' + symbol)
                print(f'PASS {dep}/lib/{abi}/{archive.name} members={len(members)} '
                      f'sha256={hashlib.sha256(archive.read_bytes()).hexdigest()}')
            except (ValueError, OSError, subprocess.CalledProcessError) as exc:
                errors.append(f'{archive}: {exc}')
    if errors:
        print('\n'.join('FAIL ' + error for error in errors), file=sys.stderr)
        return 1
    return 0

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--distribution', type=pathlib.Path,
                        default=pathlib.Path(__file__).resolve().parents[1] / 'distribution')
    parser.add_argument('--abi', choices=['arm64-v8a', 'armeabi-v7a', 'x86_64'], default='arm64-v8a')
    parser.add_argument('--tools', type=pathlib.Path, required=True)
    args = parser.parse_args()
    sys.exit(verify(args.distribution, args.abi, args.tools))
