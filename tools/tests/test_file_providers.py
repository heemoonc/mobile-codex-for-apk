"""Provider identities must survive Android's ComponentName-based registry."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
A='{http://schemas.android.com/apk/res/android}'
class FileProvidersTest(unittest.TestCase):
    def test_each_authority_has_its_own_component(self):
        providers=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').findall('./application/provider')
        names=[p.attrib[A+'name'] for p in providers]
        self.assertEqual(len(names),len(set(names)), 'Android reuses providers with the same ComponentName')
        for p in providers:
            self.assertEqual(p.attrib[A+'exported'],'false')
            self.assertEqual(p.attrib[A+'grantUriPermissions'],'true')
    def test_build_provider_exposes_only_build_cache(self):
        paths=ET.parse(ROOT/'app/src/main/res/xml/build_apk_paths.xml').getroot()
        self.assertEqual([(p.tag,p.attrib.get('path')) for p in paths],[('cache-path','build-apks/')])
if __name__=='__main__':unittest.main()
