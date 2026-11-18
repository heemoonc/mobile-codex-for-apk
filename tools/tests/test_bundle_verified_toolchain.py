import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from tools.bundle_verified_toolchain import bundle
from contextlib import redirect_stdout
from io import StringIO

class BundleVerifiedToolchainTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.source=self.root/'tools'
        for name in ['jdk-17.0.21+9','android-sdk/platforms','android-sdk/build-tools','android-sdk/licenses','bin']:(self.source/name).mkdir(parents=True,exist_ok=True)
        (self.source/'bin/aapt2').write_bytes(b'fake-verified-tool')
        (self.source/'jdk-17.0.21+9/java').write_bytes(b'fake-jdk')
        (self.source/'android-sdk/licenses/android-sdk-license').write_bytes(b'prior-acceptance')
        self.profile=self.root/'profile.json';self.profile.write_text(json.dumps({'profileId':'test','aapt2':{'archive':{'sha256':hashlib.sha256(b'fake-verified-tool').hexdigest()}}}))
        self.output=self.root/'bundle.zip';self.record=self.root/'record.json'
    def run_bundle(self):
        with redirect_stdout(StringIO()):bundle(self.source,self.output,self.profile,self.record)
    def test_fixed_sources_exclude_unrelated_data_and_preserve_license(self):
        (self.source/'private.txt').write_bytes(b'synthetic-secret')
        (self.source/'jdk-17.0.21+9/internal-link').symlink_to('java')
        self.run_bundle()
        with zipfile.ZipFile(self.output) as archive:
            self.assertNotIn('private.txt',archive.namelist())
            self.assertEqual(archive.read('jdk/internal-link'),b'fake-jdk')
            self.assertEqual(archive.read('sdk/licenses/android-sdk-license'),b'prior-acceptance')
            self.assertEqual((archive.getinfo('jdk/internal-link').external_attr>>16)&0o170000,0o100000)
        record=json.loads(self.record.read_text());self.assertEqual(record['sha256'],hashlib.sha256(self.output.read_bytes()).hexdigest())
    def test_external_link_is_rejected_before_archive_creation(self):
        outside=self.root/'synthetic-auth';outside.write_text('synthetic-secret')
        (self.source/'jdk-17.0.21+9/escape').symlink_to(outside)
        with self.assertRaisesRegex(ValueError,'source_link_escape'):self.run_bundle()
        self.assertFalse(self.output.exists())
    def test_internal_directory_link_is_rejected(self):
        (self.source/'jdk-17.0.21+9/directory').mkdir()
        (self.source/'jdk-17.0.21+9/link').symlink_to('directory',target_is_directory=True)
        with self.assertRaisesRegex(ValueError,'directory_link_unsupported'):self.run_bundle()
        self.assertFalse(self.output.exists())
