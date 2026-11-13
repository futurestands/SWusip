"""Regression checks for supported locale coverage and preserved pending resources."""
import pathlib
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

from audit_translations import audit

ROOT = pathlib.Path(__file__).resolve().parents[1]

class TranslationResources(unittest.TestCase):
    def test_empty_locale_directory_is_not_treated_as_complete(self):
        # Android lint sees locale directories even without an XML file.
        with tempfile.TemporaryDirectory(prefix='swusip-resources-') as temp:
            resources = pathlib.Path(temp)
            (resources / 'values').mkdir()
            (resources / 'values-fr').mkdir()
            (resources / 'values/strings.xml').write_text(
                '<resources><string name="account">Account</string></resources>', encoding='utf-8')
            report = audit(resources)
            self.assertEqual(report['locales'][0]['missing'], ['account'])

    def test_every_active_locale_is_complete(self):
        report = audit(ROOT / 'app/src/main/res')
        self.assertTrue(report['locales'])
        for locale in report['locales']:
            with self.subTest(locale=locale['locale']):
                self.assertFalse(locale['missing'])

    def test_ordinary_ui_labels_remain_translatable(self):
        elements = ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()
        labels = {element.get('name'): element for element in elements}
        for name in ('account', 'not_available', 'display_name', 'nickname'):
            with self.subTest(label=name):
                self.assertNotEqual(labels[name].get('translatable'), 'false')

    def test_pending_translations_preserve_upstream_content(self):
        prefix = subprocess.check_output(['git', 'rev-parse', '--show-prefix'], cwd=ROOT, text=True).strip()
        pending = list((ROOT / 'translations/pending').glob('values-*/strings.xml'))
        self.assertTrue(pending)
        for path in pending:
            with self.subTest(locale=path.parent.name):
                old = subprocess.check_output(['git', 'show',
                    'a1678b2:' + prefix + 'app/src/main/res/' + path.parent.name + '/strings.xml'],
                    cwd=ROOT, text=True, encoding='utf-8')
                self.assertEqual(path.read_text(encoding='utf-8').replace('\r\n', '\n'), old)

if __name__ == '__main__':
    unittest.main(verbosity=2)
