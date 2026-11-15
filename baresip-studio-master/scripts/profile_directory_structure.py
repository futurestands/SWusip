"""Non-sensitive host SQL/journal cost model, not an Android/Keystore benchmark.

Uses production CREATE TABLE and read-query text with synthetic in-memory rows.
Never connects to a phone, opens a customer database or runs an encrypted batch.
"""
from pathlib import Path
import hashlib
import json
import re
import sqlite3
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/kotlin/com/tutpro/baresip/CustomerDirectory.kt'


def uid(number):
    return str(uuid.UUID(int=number))


def journal_model(n):
    changes = []
    def entity(table, key):
        changes.append({'table': table, 'id': key, 'after': 'A' * 43 + '='})
    def link(table, left_key, left, right_key, right):
        changes.append({'table': table, 'leftKey': left_key, 'left': left,
                        'rightKey': right_key, 'right': right})
    scheme, phone = uid(1), uid(2)
    for i in range(n):
        account, customer = uid(3 + i * 2), uid(4 + i * 2)
        if i == 0:
            entity('scheme', scheme)
        entity('account', account)
        entity('customer', customer)
        if i == 0:
            entity('phone', phone)
        link('customer_account', 'customer_id', customer, 'account_id', account)
        link('customer_phone', 'customer_id', customer, 'phone_id', phone)
        link('account_phone', 'account_id', account, 'phone_id', phone)
    size = len(json.dumps({'createdAt': 1700000000000, 'actor': 'SYNTHETIC',
                           'changes': changes}, separators=(',', ':')).encode())
    return {'rows': n, 'shape': 'distinct customers/accounts; one shared phone and scheme',
            'journalEntries': len(changes), 'modeledJournalPlaintextBytes': size,
            'modeledStoredBase64Bytes': 4 * ((size + 12 + 16 + 2) // 3),
            'importCipherInitialisations': 2 * n + 3,
            'oneFullReadCipherInitialisations': 2 * n + 2,
            'undoJournalDecryptions': 1,
            'identityLookupsDuringImport': n + 1,
            'limits': 'Exact operation counts for this input shape; journal bytes modeled with a synthetic actor. No measured Android durations.'}


def sql_sample(source, n):
    schema = re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)', source)
    assert len(schema) == 9
    query = re.search(r'readableDatabase\.rawQuery\("""(.*?)"""', source, re.S)[1]
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    try:
        for sql in schema:
            db.execute(sql)
        scheme, phone = uid(1), uid(2)
        db.execute('INSERT INTO scheme VALUES(?,?)', (scheme, 'synthetic SQL-only value'))
        db.execute('INSERT INTO phone VALUES(?,?)', (phone, 'synthetic SQL-only value'))
        for i in range(n):
            account, customer = uid(3 + i * 2), uid(4 + i * 2)
            db.execute('INSERT INTO account VALUES(?,?,?)', (account, scheme, 'synthetic SQL-only value'))
            db.execute('INSERT INTO customer VALUES(?,?)', (customer, 'synthetic SQL-only value'))
            for table, left, right in [('customer_account', customer, account),
                                        ('customer_phone', customer, phone),
                                        ('account_phone', account, phone)]:
                db.execute(f'INSERT INTO {table} VALUES(?,?)', (left, right))
        db.commit()
        read_plan = [r[3] for r in db.execute('EXPLAIN QUERY PLAN ' + query)]
        delete_plan = [r[3] for r in db.execute('EXPLAIN QUERY PLAN DELETE FROM account WHERE id=?', (uid(3),))]
        start = time.perf_counter_ns()
        assert len(db.execute(query).fetchall()) == n
        read_ms = (time.perf_counter_ns() - start) / 1e6
        start = time.perf_counter_ns()
        with db:
            for i in reversed(range(n)):
                account, customer = uid(3 + i * 2), uid(4 + i * 2)
                db.execute('DELETE FROM account_phone WHERE account_id=? AND phone_id=?', (account, phone))
                db.execute('DELETE FROM customer_phone WHERE customer_id=? AND phone_id=?', (customer, phone))
                db.execute('DELETE FROM customer_account WHERE customer_id=? AND account_id=?', (customer, account))
                db.execute('DELETE FROM customer WHERE id=?', (customer,))
                db.execute('DELETE FROM account WHERE id=?', (account,))
        return {'rows': n, 'hostJoinMs': read_ms, 'hostReverseSqlMs': (time.perf_counter_ns() - start) / 1e6,
                'readPlan': read_plan, 'accountDeletePlan': delete_plan}
    finally:
        db.close()


if __name__ == '__main__':
    text = SOURCE.read_text(encoding='utf-8')
    report = {'kind': 'host_structure_cost_model', 'sqliteVersion': sqlite3.sqlite_version,
              'productionSourceSha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
              'limits': 'Host SQLite, synthetic placeholders, no encryption. Not Android or Keystore performance evidence.',
              'bulkShape': journal_model(10000), 'sqlSamples': [sql_sample(text, n) for n in (100, 500, 1000)]}
    target = ROOT / 'docs/evidence/directory-profiling/structure.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
