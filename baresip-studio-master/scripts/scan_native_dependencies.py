"""Query OSV for public locked native commits; this is a limited coverage scan."""
import argparse
import datetime
import json
import pathlib
import sys
import urllib.request

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=pathlib.Path,
                    default=root / 'docs/evidence/native-osv-scan.json')
args = parser.parse_args()
lock = json.loads((root / 'scripts/native-sources.lock.json').read_text())
request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
    data=json.dumps({'queries': [{'commit': entry['commit']} for entry in lock.values()]}).encode(),
    headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=45) as response:
    results = json.load(response)['results']
if len(results) != len(lock):
    raise RuntimeError('Incomplete OSV response')
report = {'scanned_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'coverage': 'Public native commit queries only; not a complete transitive/package scan.',
          'dependencies': []}
found = False
for (name, source), result in zip(lock.items(), results):
    advisories = result.get('vulns', [])
    report['dependencies'].append({'name': name, 'commit': source['commit'], 'advisories': advisories})
    print(f'{name}: {len(advisories)} matching commit advisories')
    found |= bool(advisories)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(report['coverage'])
sys.exit(1 if found else 0)
