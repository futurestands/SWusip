"""Check that production JNI still delegates essential operations to Baresip."""
import pathlib
import re
import sys

def check_source(source):
    required = ['libre_init', 'baresip_init', 'ua_init', 'ua_register',
                'call_connect', 'call_transfer', 'ua_answer', 'ua_hangup', 'bevent_register']
    errors = [f'Missing native engine call: {name}' for name in required
              if not re.search(r'(?<![\w])' + name + r'\s*\(', source)]
    exports = set(re.findall(r'Java_com_tutpro_baresip_\w+', source))
    if len(exports) < 90:
        errors.append(f'JNI surface unexpectedly reduced: {len(exports)} exports')
    return errors, len(exports)

if __name__ == '__main__':
    root = pathlib.Path(__file__).resolve().parents[1]
    source = (root / 'app/src/main/cpp/baresip.c').read_text(encoding='utf-8')
    errors, count = check_source(source)
    for path in (root / 'app/src/main').rglob('*'):
        if path.is_file() and path.name.lower() in ('mock_engine.c', 'create_stub_distribution.py'):
            errors.append(f'Prohibited production source: {path.relative_to(root)}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        sys.exit(1)
    print(f'PASS: {count} JNI exports; all essential calls delegate to the native engine.')
    print('Static guard only; binary provenance, symbol/ABI checks and real SIP tests remain required.')
