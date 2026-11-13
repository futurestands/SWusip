"""Audit active Android translations without disabling lint or inventing translations."""
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]

def audit(resources):
    default = ET.parse(resources / 'values/strings.xml').getroot()
    names = {element.attrib['name'] for element in default
             if element.tag in {'string', 'plurals', 'string-array'}
             and element.get('translatable') != 'false'}
    results = []
    for directory in sorted(resources.glob('values-*')):
        if not directory.is_dir() or not re.fullmatch(r'values-[a-z]{2,3}(?:-r[A-Z]{2})?', directory.name):
            continue
        path = directory / 'strings.xml'
        elements = ET.parse(path).getroot() if path.exists() else []
        present = {element.get('name') for element in elements}
        results.append({'locale': directory.name,
                        'translated': len(names & present),
                        'missing': sorted(names - present)})
    return {'default_translatable_count': len(names), 'locales': results}

if __name__ == '__main__':
    result = audit(ROOT / 'app/src/main/res')
    print(json.dumps(result, indent=2))
    sys.exit(1 if any(entry['missing'] for entry in result['locales']) else 0)
