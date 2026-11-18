"""Explicit-consent, pinned-source Android tools installer. No project code executes here."""
import argparse
import ctypes
import errno
import fcntl
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import signal
import stat
import sys
import tarfile
import tempfile
import urllib.parse
import urllib.request
import zipfile

HOSTS = {'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com', 'dl.google.com'}
MAX_ENTRIES = 20000

def progress(phase, **fields):
    print(json.dumps({'phase': phase, **fields}, ensure_ascii=True), flush=True)

def validate_consent(profile, text, accepted):
    digest = hashlib.sha256(text.encode()).hexdigest()
    if digest != profile['licenseSha256'] or accepted != digest:
        raise ValueError('SDK license consent does not match the displayed terms')
    if hashlib.sha1(text.strip().encode()).hexdigest() != profile['licenseSha1']:
        raise ValueError('SDK license hash mismatch')

def validate_url(url):
    u = urllib.parse.urlsplit(url)
    if u.scheme != 'https' or u.hostname not in HOSTS or u.username or u.password or u.port not in (None, 443):
        raise ValueError('Download URL must use a pinned HTTPS source')

class Redirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl)
        if getattr(req, '_hops', 0) >= 4:
            raise ValueError('Too many download redirects')
        result = super().redirect_request(req, fp, code, msg, headers, newurl)
        result._hops = getattr(req, '_hops', 0) + 1
        return result

def copy_verified(source, destination, size, sha256, cancelled=lambda: False, update=lambda n: None):
    count = 0; digest = hashlib.sha256()
    while True:
        if cancelled(): raise InterruptedError('Cancelled')
        data = source.read(65536)
        if not data: break
        count += len(data)
        if count > size: raise ValueError('Download exceeds pinned size')
        digest.update(data); destination.write(data); update(count)
    if count != size or digest.hexdigest() != sha256:
        raise ValueError('Download size or SHA-256 mismatch; retry the download')

def download(package, destination):
    validate_url(package['url'])
    # No cookies, credential managers, environment proxy credentials or auth headers.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), Redirects())
    request = urllib.request.Request(package['url'], headers={'User-Agent': 'Mobile-Codex-APK-Setup/1'})
    last = [-1]
    def update(n):
        percent = n * 100 // package['size']
        if percent != last[0]:
            last[0] = percent; progress('downloading', package=package['id'], percent=percent)
    with opener.open(request, timeout=30) as response, destination.open('xb') as output:
        if response.status != 200: raise ValueError('Download HTTP status is not 200')
        length = response.headers.get('Content-Length')
        if length is not None and int(length) != package['size']: raise ValueError('Unexpected download size')
        copy_verified(response, output, package['size'], package['sha256'], update=update)

def relative(name, prefix):
    if not name or name.startswith('/') or '\\' in name or any(ord(c)<32 for c in name):
        raise ValueError('Unsafe archive path')
    parts = name.rstrip('/').split('/')
    if any(p in ('', '.', '..') for p in parts) or parts[0] != prefix:
        raise ValueError('Unexpected archive path')
    return Path(*parts[1:])

def destination_path(destination, rel):
    path = destination / rel
    for parent in [destination, *path.parents]:
        if parent == destination.parent: break
        if parent.is_symlink(): raise ValueError('Archive entry traverses a link')
    return path

def extract_zip(archive, destination, prefix, limit=1024*1024*1024):
    seen = set(); total = 0
    with zipfile.ZipFile(archive) as z:
        if len(z.infolist()) > MAX_ENTRIES: raise ValueError('Too many archive entries')
        for item in z.infolist():
            rel = relative(item.filename, prefix)
            if str(rel) in seen: raise ValueError('Duplicate archive path')
            seen.add(str(rel)); mode = item.external_attr >> 16
            if stat.S_IFMT(mode) not in (0, stat.S_IFDIR, stat.S_IFREG): raise ValueError('Unsupported ZIP entry')
            total += item.file_size
            if total > limit: raise ValueError('Archive expanded size limit exceeded')
            path = destination_path(destination, rel)
            if item.is_dir(): path.mkdir(parents=True, exist_ok=True); continue
            path.parent.mkdir(parents=True, exist_ok=True)
            with z.open(item) as source, path.open('xb') as output:
                written = 0
                while True:
                    block = source.read(65536)
                    if not block: break
                    written += len(block)
                    if written > item.file_size: raise ValueError('Invalid archive entry size')
                    output.write(block)
                if written != item.file_size: raise ValueError('Truncated archive entry')
            path.chmod(0o755 if mode & 0o111 else 0o644)
    return total

def extract_tar(archive, destination, prefix, limit=1024*1024*1024):
    seen=set(); total=0; links=[]; count=0
    with tarfile.open(archive, 'r:gz') as z:
        for item in z:
            count += 1
            if count > MAX_ENTRIES: raise ValueError('Too many archive entries')
            rel = relative(item.name, prefix)
            if str(rel) in seen: raise ValueError('Duplicate archive path')
            seen.add(str(rel)); path=destination_path(destination,rel)
            if item.isdir(): path.mkdir(parents=True,exist_ok=True); continue
            if item.issym():
                link=item.linkname
                if not link or link.startswith('/') or '\\' in link or any(ord(c)<32 for c in link): raise ValueError('Unsafe link')
                resolved=os.path.normpath(str(rel.parent / link))
                if resolved=='..' or resolved.startswith('../'): raise ValueError('Link escapes destination')
                links.append((path,link));continue
            if not item.isfile(): raise ValueError('Unsupported TAR entry')
            total += item.size
            if total > limit: raise ValueError('Archive expanded size limit exceeded')
            path.parent.mkdir(parents=True,exist_ok=True)
            with z.extractfile(item) as source,path.open('xb') as output:
                shutil.copyfileobj(source,output,65536)
            if path.stat().st_size != item.size: raise ValueError('Truncated archive entry')
            path.chmod(0o755 if item.mode & 0o111 else 0o644)
        # Create links last, so no later entry can write through one.
        for path,link in links:
            path.parent.mkdir(parents=True,exist_ok=True);path.symlink_to(link)
        for path,_ in links:
            if not path.resolve().is_relative_to(destination.resolve()): raise ValueError('Link chain escapes destination')
    return total

def commit(stage, target):
    # Kernel-level NOREPLACE prevents even an empty existing installation being replaced.
    libc = ctypes.CDLL(None, use_errno=True)
    try:
        rename = libc.renameat2
    except AttributeError:
        # Bionic API 29 has the Linux syscall but not the API 30 libc wrapper.
        numbers={'aarch64':276,'arm64':276,'x86_64':316}
        number=numbers.get(os.uname().machine)
        if number is None: raise ValueError('Atomic installation is unsupported on this architecture')
        libc.syscall.restype=ctypes.c_long
        result=libc.syscall(ctypes.c_long(number),ctypes.c_int(-100),ctypes.c_char_p(os.fsencode(stage)),ctypes.c_int(-100),ctypes.c_char_p(os.fsencode(target)),ctypes.c_uint(1))
    else:
        rename.argtypes=[ctypes.c_int,ctypes.c_char_p,ctypes.c_int,ctypes.c_char_p,ctypes.c_uint]
        rename.restype=ctypes.c_int
        result=rename(-100,os.fsencode(stage),-100,os.fsencode(target),1)
    if result:
        error=ctypes.get_errno()
        raise OSError(error,os.strerror(error),str(target))

OWNER='mobile-codex-android-tools-v1'

@contextmanager
def installer_lock(opt):
    descriptor=os.open(opt/'.android-download.lock',os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW|os.O_CLOEXEC,0o600)
    try:
        try: fcntl.flock(descriptor,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError: raise ValueError('Another tools installation is still running')
        yield
    finally:
        os.close(descriptor)

def recover_staging(opt):
    # Caller holds the kernel lock, which is released automatically on process death.
    for stage in opt.glob('.android-download-*'):
        if stage.is_symlink() or not stage.is_dir(): continue
        marker=stage/'.owner'
        try:
            fd=os.open(marker,os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC)
            with os.fdopen(fd,'rb') as source:
                owned=stat.S_ISREG(os.fstat(source.fileno()).st_mode) and source.read(128)==OWNER.encode()
        except (FileNotFoundError,OSError): continue
        if owned: shutil.rmtree(stage)

def install(root, profile, text, accepted, fetch=download):
    validate_consent(profile,text,accepted)
    root=Path(root)
    if root.is_symlink() or not root.is_dir(): raise ValueError('Linux root is unavailable')
    opt=root/'opt'
    if opt.is_symlink(): raise ValueError('Linux opt must not be a symlink')
    opt.mkdir(exist_ok=True)
    with installer_lock(opt):
        recover_staging(opt)
        target=opt/'android'
        if os.path.lexists(target): raise FileExistsError('Existing /opt/android is preserved; use execution check')
        if shutil.disk_usage(opt).free < profile['requiredFreeBytes']: raise ValueError('At least 1500 MiB of free space is required')
        with tempfile.TemporaryDirectory(prefix='.android-download-',dir=opt) as temporary:
            work=Path(temporary);(work/'.owner').write_text(OWNER);stage=work/'android';stage.mkdir();total=0
            for package in profile['packages']:
                archive=work/(package['id']+'.download')
                fetch(package,archive)
                progress('extracting',package=package['id'])
                dest=stage/package['destination']
                if package['kind']=='file':
                    dest.parent.mkdir(parents=True,exist_ok=True);archive.rename(dest);dest.chmod(0o755);total+=dest.stat().st_size
                else:
                    dest.mkdir(parents=True)
                    extract=extract_tar if package['kind']=='tar' else extract_zip
                    total+=extract(archive,dest,package['prefix'],profile['expandedLimit']-total);archive.unlink()
                if total>profile['expandedLimit']: raise ValueError('Expanded size exceeds bound')
            for name in ['jdk/bin/java','jdk/bin/javac','sdk/platforms/android-35/android.jar','sdk/build-tools/35.0.0/lib/d8.jar','bin/aapt2']:
                if not (stage/name).is_file(): raise ValueError('Required tool is missing: '+name)
            licenses=stage/'sdk/licenses';licenses.mkdir(parents=True)
            (licenses/'android-sdk-license').write_text(profile['licenseSha1']+'\n')
            (stage/'SDK-LICENSE.txt').write_text(text)
            (stage/'profile.json').write_text(json.dumps({'profileId':profile['profileId'],'sources':profile['packages'],'executionVerified':False}))
            progress('installing');commit(stage,target)
    progress('installed');print('MC_ANDROID_TOOLS=installed',flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',required=True);parser.add_argument('--accepted-license',required=True)
    args=parser.parse_args();assets=Path(__file__).resolve().parent
    def stop(*_): raise InterruptedError('Download/install cancelled')
    signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
    try:
        install(args.root,json.loads((assets/'build-tools-download.json').read_text()),(assets/'android-sdk-license.txt').read_text(),args.accepted_license)
        return 0
    except Exception as error:
        print('Setup failed: '+str(error),file=sys.stderr,flush=True);return 1

if __name__=='__main__': sys.exit(main())
