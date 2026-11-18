import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import subprocess
import select
import sys
from unittest.mock import patch
import zipfile

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'

class DownloadTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('tools', ASSETS / 'android_tools_download.py')
        cls.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.m)

    def test_consent_is_bound_to_bundled_license(self):
        p = json.loads((ASSETS / 'build-tools-download.json').read_text())
        license_text = (ASSETS / 'android-sdk-license.txt').read_text()
        self.assertEqual(hashlib.sha1(license_text.strip().encode()).hexdigest(), p['licenseSha1'])
        self.m.validate_consent(p, license_text, p['licenseSha256'])
        for consent in ['', 'true', '0' * 64]:
            with self.assertRaises(ValueError): self.m.validate_consent(p, license_text, consent)
        with self.assertRaises(ValueError): self.m.validate_consent(p, license_text + 'changed', p['licenseSha256'])

    def test_copy_rejects_short_large_corrupt_and_cancelled_data(self):
        data = b'hello'; sha = hashlib.sha256(data).hexdigest()
        self.m.copy_verified(io.BytesIO(data), io.BytesIO(), len(data), sha)
        for value in [b'hell', b'helloo', b'wrong']:
            with self.assertRaises(ValueError): self.m.copy_verified(io.BytesIO(value), io.BytesIO(), len(data), sha)
        with self.assertRaises(InterruptedError):
            self.m.copy_verified(io.BytesIO(data), io.BytesIO(), len(data), sha, lambda: True)

    def test_zip_blocks_traversal_symlinks_duplicates_and_expansion(self):
        for names in [[('../escape', b'x', 0)], [('pkg/link', b'../x', 0o120777)], [('pkg/x', b'x', 0), ('pkg/x', b'x', 0)], [('pkg/x', b'12345', 0)]]:
            with tempfile.TemporaryDirectory() as d:
                archive = Path(d) / 'input.zip'; dest = Path(d) / 'dest'; dest.mkdir()
                with zipfile.ZipFile(archive, 'w') as z:
                    for name, data, mode in names:
                        info = zipfile.ZipInfo(name); info.external_attr = mode << 16; z.writestr(info, data)
                with self.assertRaises(ValueError): self.m.extract_zip(archive, dest, 'pkg', limit=4)
                self.assertFalse((Path(d) / 'escape').exists())

    def test_tar_keeps_internal_license_links_but_blocks_escape_and_devices(self):
        for name, link, kind in [('jdk/legal/LICENSE', '../base/LICENSE', tarfile.SYMTYPE), ('jdk/link', '../../escape', tarfile.SYMTYPE), ('jdk/dev', '', tarfile.CHRTYPE)]:
            with tempfile.TemporaryDirectory() as d:
                archive = Path(d) / 'input.tar.gz'; dest = Path(d) / 'dest'; dest.mkdir()
                with tarfile.open(archive, 'w:gz') as z:
                    x = tarfile.TarInfo(name); x.type = kind; x.linkname = link; z.addfile(x)
                if name.startswith('jdk/legal'):
                    self.m.extract_tar(archive, dest, 'jdk'); self.assertTrue((dest/'legal/LICENSE').is_symlink())
                else:
                    with self.assertRaises(ValueError): self.m.extract_tar(archive, dest, 'jdk')

    def test_atomic_commit_never_replaces_existing_directory_or_symlink(self):
        for existing in ['directory', 'symlink', 'empty']:
            with tempfile.TemporaryDirectory() as d:
                stage=Path(d)/'stage'; stage.mkdir(); (stage/'new').write_text('new')
                target=Path(d)/'android'
                if existing=='symlink': target.symlink_to(Path(d)/'other')
                else:
                    target.mkdir()
                    if existing=='directory': (target/'original').write_text('keep')
                with self.assertRaises(FileExistsError): self.m.commit(stage,target)
                self.assertTrue(stage.is_dir())
                if existing=='directory': self.assertEqual((target/'original').read_text(),'keep')
        with tempfile.TemporaryDirectory() as d:
            stage=Path(d)/'stage';stage.mkdir();(stage/'new').write_text('ok')
            target=Path(d)/'android';self.m.commit(stage,target);self.assertEqual((target/'new').read_text(),'ok')

    def test_interrupted_install_cleans_only_its_stage_and_retry_is_possible(self):
        p=json.loads((ASSETS/'build-tools-download.json').read_text());text=(ASSETS/'android-sdk-license.txt').read_text()
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'opt').mkdir();(root/'opt/unrelated').write_text('keep')
            calls=[]
            def interrupted(package,path):
                calls.append(package['id']);path.write_bytes(b'partial');raise InterruptedError('cancelled')
            with self.assertRaises(ValueError):self.m.install(root,p,text,'',interrupted)
            self.assertEqual(calls,[])
            for _ in range(2):
                with self.assertRaises(InterruptedError):self.m.install(root,p,text,p['licenseSha256'],interrupted)
                self.assertEqual(set(x.name for x in (root/'opt').iterdir()),{'unrelated','.android-download.lock'})
            self.assertEqual((root/'opt/unrelated').read_text(),'keep')
            (root/'opt/android').mkdir()
            with self.assertRaises(FileExistsError):self.m.install(root,p,text,p['licenseSha256'],interrupted)
            self.assertEqual(len(calls),2)

    def test_api29_commit_falls_back_to_kernel_no_replace(self):
        real=self.m.ctypes.CDLL(None,use_errno=True)
        class Api29:
            syscall=real.syscall
        with tempfile.TemporaryDirectory() as d, patch.object(self.m.ctypes,'CDLL',lambda *a,**kw:Api29()):
            root=Path(d);stage=root/'stage';stage.mkdir();(stage/'new').write_text('ok')
            target=root/'android';self.m.commit(stage,target)
            self.assertEqual((target/'new').read_text(),'ok')
            stage.mkdir()
            with self.assertRaises(FileExistsError):self.m.commit(stage,target)

    def test_killed_install_stage_is_reclaimed_but_unowned_directories_are_preserved(self):
        p=json.loads((ASSETS/'build-tools-download.json').read_text());text=(ASSETS/'android-sdk-license.txt').read_text()
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);opt=root/'opt';opt.mkdir()
            stale=opt/'.android-download-killed';stale.mkdir();(stale/'.owner').write_text('mobile-codex-android-tools-v1');(stale/'partial').write_bytes(b'partial')
            unrelated=opt/'.android-download-not-ours';unrelated.mkdir();(unrelated/'keep').write_text('keep')
            outside=root/'outside';outside.mkdir();(outside/'.owner').write_text('mobile-codex-android-tools-v1');(outside/'keep').write_text('keep')
            linked=opt/'.android-download-linked';linked.symlink_to(outside,target_is_directory=True)
            def interrupted(package,path): raise InterruptedError('cancelled')
            with self.assertRaises(InterruptedError):self.m.install(root,p,text,p['licenseSha256'],interrupted)
            self.assertFalse(stale.exists());self.assertEqual((unrelated/'keep').read_text(),'keep');self.assertTrue(linked.is_symlink());self.assertEqual((outside/'keep').read_text(),'keep')

    def test_process_kill_releases_lock_and_next_install_reclaims_actual_stage(self):
        p=json.loads((ASSETS/'build-tools-download.json').read_text());text=(ASSETS/'android-sdk-license.txt').read_text()
        with tempfile.TemporaryDirectory() as d:
            child_code="""import importlib.util,json,sys,time
from pathlib import Path
assets=Path(sys.argv[1]);spec=importlib.util.spec_from_file_location('tools',assets/'android_tools_download.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
p=json.loads((assets/'build-tools-download.json').read_text())
def block(package,path):
 path.write_bytes(b'partial');print('ready',flush=True);time.sleep(300)
m.install(Path(sys.argv[2]),p,(assets/'android-sdk-license.txt').read_text(),p['licenseSha256'],fetch=block)
"""
            process=subprocess.Popen([sys.executable,'-c',child_code,str(ASSETS),d],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            try:
                self.assertTrue(select.select([process.stdout],[],[],10)[0], 'installer did not reach downloading')
                self.assertEqual(process.stdout.readline().strip(),'ready')
                process.kill();process.wait(timeout=10)
                self.assertEqual(len(list((Path(d)/'opt').glob('.android-download-*'))),1)
                def interrupted(package,path):raise InterruptedError('cancelled')
                with self.assertRaises(InterruptedError):self.m.install(Path(d),p,text,p['licenseSha256'],interrupted)
                self.assertEqual(list((Path(d)/'opt').glob('.android-download-*')),[])
            finally:
                if process.poll() is None:process.kill();process.wait()
                process.stdout.close();process.stderr.close()

    def test_https_redirect_policy_rejects_untrusted_or_plaintext_hosts(self):
        for url in ['http://dl.google.com/x','https://evil.example/x','https://user:pass@github.com/x','https://github.com:444/x']:
            with self.assertRaises(ValueError): self.m.validate_url(url)
        for url in ['https://dl.google.com/a','https://github.com/a','https://release-assets.githubusercontent.com/a']:
            self.m.validate_url(url)

if __name__ == '__main__': unittest.main()
