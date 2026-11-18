import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path
from tools.import_release_runtime import import_runtime

class ImportRuntimeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.apk = self.root/'release.apk'
        self.target = self.root/'staging'
        self.files = {
            'assets/devtools/manifest.json': b'{}',
            'assets/devtools/payload.zip': b'payload',
            'assets/runtime/manifest.json': b'{}',
            'assets/runtime/CODEX-LICENSE.txt': b'license',
            'assets/runtime/CODEX-NOTICE.txt': b'notice',
            'lib/arm64-v8a/libcodex.so': b'codex',
            'lib/arm64-v8a/libcodexmodehostx.so': b'helper',
            'lib/arm64-v8a/libc++_shared.so': b'c++',
            'assets/legal/WEBRTC-LICENSES.md': b'webrtc',
            'assets/web/index.html': b'not copied',
        }
    def archive(self):
        with zipfile.ZipFile(self.apk,'w') as z:
            for name,data in self.files.items(): z.writestr(name,data)
        return hashlib.sha256(self.apk.read_bytes()).hexdigest()
    def test_allowed_files_are_copied_with_hashes(self):
        result = import_runtime(self.apk,self.target,self.archive())
        self.assertEqual((self.target/'jniLibs/arm64-v8a/libcodex.so').read_bytes(),b'codex')
        self.assertFalse((self.target/'assets/web').exists())
        self.assertEqual(result['files']['jniLibs/arm64-v8a/libcodex.so']['sha256'],hashlib.sha256(b'codex').hexdigest())
    def test_wrong_release_hash_leaves_no_target(self):
        self.archive()
        with self.assertRaisesRegex(ValueError,'hash_mismatch'):
            import_runtime(self.apk,self.target,'0'*64)
        self.assertFalse(self.target.exists())
    def test_traversal_entry_is_rejected(self):
        self.files['assets/runtime/../../escape']=b'bad'
        digest=self.archive()
        with self.assertRaisesRegex(ValueError,'unsafe_entry'):
            import_runtime(self.apk,self.target,digest)
        self.assertFalse(self.target.exists())
    def test_missing_runtime_is_rejected_before_install(self):
        del self.files['lib/arm64-v8a/libcodex.so']
        digest=self.archive()
        with self.assertRaisesRegex(ValueError,'missing_runtime'):
            import_runtime(self.apk,self.target,digest)
        self.assertFalse(self.target.exists())
    def test_existing_target_is_preserved(self):
        digest=self.archive();self.target.mkdir();(self.target/'keep').write_text('keep')
        with self.assertRaises(FileExistsError):
            import_runtime(self.apk,self.target,digest)
        self.assertEqual((self.target/'keep').read_text(),'keep')

if __name__=='__main__': unittest.main()
