"""Import pinned release runtime binaries for a local bootstrap build, not a native rebuild."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tempfile
import zipfile

RELEASE_SHA256 = '48f05bd8812a5907a4e80bf8d228afc754875f497c0c380e94a06e1db2ec6731'
REQUIRED = {
    'assets/devtools/manifest.json', 'assets/devtools/payload.zip',
    'assets/runtime/manifest.json', 'assets/runtime/CODEX-LICENSE.txt',
    'assets/runtime/CODEX-NOTICE.txt', 'lib/arm64-v8a/libcodex.so',
    'lib/arm64-v8a/libcodexmodehostx.so', 'lib/arm64-v8a/libc++_shared.so',
    'assets/legal/WEBRTC-LICENSES.md',
}

def import_runtime(apk: Path, destination: Path, expected_sha256: str) -> dict:
    if destination.exists() or destination.is_symlink():
        raise FileExistsError('target_exists')
    with apk.open('rb') as source:
        digest = hashlib.file_digest(source, 'sha256').hexdigest()
    if digest != expected_sha256:
        raise ValueError('hash_mismatch')
    with zipfile.ZipFile(apk) as archive:
        entries = []
        seen = set()
        total = 0
        for item in archive.infolist():
            name = item.filename
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or '\\' in name:
                raise ValueError('unsafe_entry')
            if item.is_dir():
                continue
            if name.startswith(('assets/devtools/','assets/runtime/','assets/legal/','lib/arm64-v8a/')):
                if name in seen or (item.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError('unsafe_entry')
                seen.add(name)
                total += item.file_size
                if total > 1024*1024*1024 or len(seen) > 2048:
                    raise ValueError('archive_limit')
                relative = 'jniLibs/' + name[4:] if name.startswith('lib/') else name
                entries.append((item,relative))
        if not REQUIRED.issubset(seen):
            raise ValueError('missing_runtime')
        destination.parent.mkdir(parents=True,exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix='.runtime-',dir=destination.parent))
        try:
            files = {}
            for item,relative in entries:
                output = staging/relative
                output.parent.mkdir(parents=True,exist_ok=True)
                with archive.open(item) as source, output.open('xb') as target:
                    shutil.copyfileobj(source,target)
                with output.open('rb') as content:
                    sha = hashlib.file_digest(content,'sha256').hexdigest()
                files[relative] = {'sha256':sha,'size':item.file_size}
            # Validate published tool hashes before any files become active.
            manifest = json.loads((staging/'assets/devtools/manifest.json').read_text())
            for name,record in manifest.get('nativeFiles',{}).items():
                if '/' in name or '\\' in name or name in ('.','..'):
                    raise ValueError('unsafe_manifest')
                value = files.get('jniLibs/arm64-v8a/'+name,{})
                if value.get('sha256') != record['sha256']:
                    raise ValueError('native_hash_mismatch')
            staging.rename(destination)
            return {'sourceSha256':digest,'nativeRebuilt':False,'files':files}
        except BaseException:
            shutil.rmtree(staging)
            raise

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('destination',type=Path)
    parser.add_argument('--record',type=Path,required=True)
    args=parser.parse_args()
    result=import_runtime(args.apk,args.destination,RELEASE_SHA256)
    result.update(source='https://github.com/nokryong/mobile-codex/releases/tag/v0.3.9',
                  sourceCommit='b6c12eb65fd29a38b0c2825bf12a7aca64736c5a')
    args.record.write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__': main()
