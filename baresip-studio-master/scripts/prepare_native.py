"""Resolve once, then fetch immutable native sources in an isolated cache.

Run --resolve only when deliberately updating the lock. Normal runs use only
the committed revisions. Exports come from committed Git objects, not worktrees.
"""
import argparse
import concurrent.futures
import hashlib
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCK = ROOT / 'scripts/native-sources.lock.json'
BUILDER_COMMIT = 'bf8448d8db4c3ff0ad3dabdd2d957954fed8ce22'
SOURCES = {
    'amr': ('https://git.code.sf.net/p/opencore-amr/code', 'refs/tags/v0.1.6'),
    'baresip': ('https://github.com/baresip/baresip.git', 'refs/tags/v4.13.0'),
    'bcg729': ('https://github.com/BelledonneCommunications/bcg729.git', 'refs/heads/release/1.1.1'),
    'codec2': ('https://github.com/drowe67/codec2.git', 'refs/tags/1.2.0'),
    'g722': ('https://github.com/sippy/libg722.git', 'refs/tags/v1.2.2'),
    'g7221': ('https://github.com/freeswitch/libg7221.git', 'refs/heads/master'),
    'ilbc': ('https://github.com/juha-h/libilbc.git', 'refs/heads/main'),
    'openssl': ('https://github.com/openssl/openssl.git', 'refs/heads/openssl-4.0'),
    'opus': ('https://github.com/xiph/opus.git', 'refs/tags/v1.4'),
    're': ('https://github.com/baresip/re.git', 'refs/tags/v4.13.0'),
    'sndfile': ('https://github.com/juha-h/libsndfile.git', 'refs/heads/master'),
    'vo-amrwbenc': ('https://git.code.sf.net/p/opencore-amr/vo-amrwbenc', 'HEAD'),
    'zrtpcpp': ('https://github.com/juha-h/ZRTPCPP.git', 'refs/heads/master'),
}

def git(*args, cwd=None):
    return subprocess.check_output(['git', *args], cwd=cwd, text=True).strip()

def resolve(item):
    name, (url, ref) = item
    lines = git('ls-remote', url, ref, ref + '^{}').splitlines()
    if not lines:
        raise RuntimeError(f'Cannot resolve {name}: {ref}')
    revision = lines[-1].split()[0]
    print(f'PIN {name} {revision}', flush=True)
    return name, {'url': url, 'ref': ref, 'commit': revision}

def prepare_builder():
    path = ROOT / '.native-build/libbaresip-android'
    if not path.exists():
        path.mkdir(parents=True)
        git('init', cwd=path)
        git('remote', 'add', 'origin', 'https://github.com/juha-h/libbaresip-android.git', cwd=path)
        git('fetch', '--depth=1', 'origin', BUILDER_COMMIT, cwd=path)
        git('checkout', '--detach', 'FETCH_HEAD', cwd=path)
    if not (path / '.git').exists() or git('rev-parse', 'HEAD', cwd=path) != BUILDER_COMMIT:
        raise RuntimeError('Native builder is not at the project gitlink revision')
    git('-c', 'core.autocrlf=false', 'archive', '--format=tar',
        '--output=' + str(ROOT / '.native-build/native-builder.tar'), BUILDER_COMMIT, cwd=path)

def fetch(item):
    name, source = item
    path = ROOT / '.native-build/libbaresip-android' / name
    if path.exists():
        if not (path / '.git').exists():
            raise RuntimeError(f'Refusing to overwrite unverified source {path}')
        actual = git('rev-parse', 'HEAD', cwd=path)
        if actual != source['commit']:
            raise RuntimeError(f'Wrong existing revision: {path}: {actual}')
    else:
        path.mkdir(parents=True)
        git('init', cwd=path)
        git('config', 'core.autocrlf', 'false', cwd=path)
        git('remote', 'add', 'origin', source['url'], cwd=path)
        git('fetch', '--depth=1', 'origin', source['commit'], cwd=path)
        git('checkout', '--detach', 'FETCH_HEAD', cwd=path)
    print(f'FETCHED {name} {source["commit"]}', flush=True)
    # git archive preserves upstream LF endings and executable bits even when
    # Windows has core.autocrlf=true. Build these exports on the Linux filesystem.
    archive_dir = ROOT / '.native-build/sources'
    archive_dir.mkdir(exist_ok=True)
    git('-c', 'core.autocrlf=false', 'archive', '--format=tar', '--output=' + str(archive_dir / (name + '.tar')),
        source['commit'], cwd=path)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resolve', action='store_true')
    args = parser.parse_args()
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        if args.resolve:
            lock = dict(pool.map(resolve, SOURCES.items()))
            LOCK.write_text(json.dumps(lock, indent=2) + '\n', encoding='utf-8')
        else:
            prepare_builder()
            lock = json.loads(LOCK.read_text(encoding='utf-8'))
            list(pool.map(fetch, lock.items()))
            archives = sorted((ROOT / '.native-build/sources').glob('*.tar'))
            archives.append(ROOT / '.native-build/native-builder.tar')
            hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in archives}
            evidence = ROOT / 'docs/evidence/native-source-archive-sha256.json'
            evidence.parent.mkdir(parents=True, exist_ok=True)
            evidence.write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
