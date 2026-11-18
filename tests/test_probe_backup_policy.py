"""Probe data must remain local even when an OEM offers device transfer."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
PROJECTS = ('android-library-probe', 'android-storage-probe', 'android-ui-library-probe', 'android-work-di-probe', 'android-notes-integration')
DOMAINS = ('root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref')

class ProbeBackupPolicyTest(unittest.TestCase):
    def test_cloud_and_device_transfer_exclude_all_probe_data(self):
        for project in PROJECTS:
            with self.subTest(project=project):
                main = ROOT / 'tools' / project / 'app/src/main'
                app = ET.parse(main/'AndroidManifest.xml').getroot().find('application')
                self.assertEqual(app.get(ANDROID+'allowBackup'), 'false')
                self.assertEqual(app.get(ANDROID+'fullBackupContent'), 'false')
                resource = app.get(ANDROID+'dataExtractionRules')
                self.assertIsNotNone(resource, 'Modern transfer rules are missing')
                self.assertTrue(resource.startswith('@xml/'))
                rules = ET.parse(main/'res/xml'/(resource.split('/')[-1]+'.xml')).getroot()
                for mode in ('cloud-backup', 'device-transfer'):
                    section = rules.find(mode)
                    self.assertIsNotNone(section, mode)
                    self.assertFalse(section.findall('include'))
                    exclusions = {(x.get('domain'),x.get('path')) for x in section.findall('exclude')}
                    for domain in DOMAINS:
                        self.assertIn((domain,'.'),exclusions, (mode,domain))

if __name__ == '__main__': unittest.main()
