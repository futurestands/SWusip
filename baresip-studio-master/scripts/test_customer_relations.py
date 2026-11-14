"""Exercise the production relational query with synthetic SQLite data.

This structural regression does not substitute for authenticated Android AES/Keystore
repository tests. It neither opens the handset database nor bypasses its access gate.
"""
from pathlib import Path
import re
import sqlite3
import unittest


class CustomerRelations(unittest.TestCase):
    def setUp(self):
        source = (Path(__file__).resolve().parents[1] /
                  'app/src/main/kotlin/com/tutpro/baresip/CustomerDirectory.kt').read_text(encoding='utf-8')
        self.db = sqlite3.connect(':memory:')
        self.addCleanup(self.db.close)
        self.db.execute('PRAGMA foreign_keys=ON')
        for schema in re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)', source):
            self.db.execute(schema)
        self.query = re.search(r'readableDatabase\.rawQuery\("""(SELECT c\.customer_id,.*?)""", null\)', source, re.S).group(1)
        self.db.execute("INSERT INTO scheme VALUES('s','synthetic-schema-marker')")
        self.db.executemany("INSERT INTO account VALUES(?, 's', 'synthetic-schema-marker')", [('a1',), ('a2',)])
        self.db.executemany("INSERT INTO customer VALUES(?, 'synthetic-schema-marker')", [('c1',), ('c2',)])
        self.db.executemany("INSERT INTO phone VALUES(?, 'synthetic-schema-marker')", [('p1',), ('p2',)])
        self.db.executemany('INSERT INTO customer_phone VALUES(?,?)', [('c1','p1'),('c1','p2'),('c2','p1')])
        self.db.executemany('INSERT INTO customer_account VALUES(?,?)', [('c1','a1'),('c1','a2'),('c2','a1')])
        self.db.executemany('INSERT INTO account_phone VALUES(?,?)', [('a1','p1'),('a2','p2')])

    def test_multiple_accounts_do_not_cross_match_unrelated_phones(self):
        self.assertEqual({('c1','a1','p1','s'),('c1','a2','p2','s'),('c2','a1','p1','s')},
                         set(self.db.execute(self.query)))

    def test_shared_phone_keeps_both_authorised_customer_associations(self):
        matches = [row for row in self.db.execute(self.query) if row[2] == 'p1']
        self.assertEqual({'c1','c2'}, {row[0] for row in matches})
        self.assertTrue(all(row[1] == 'a1' for row in matches))


if __name__ == '__main__':
    unittest.main(verbosity=2)
