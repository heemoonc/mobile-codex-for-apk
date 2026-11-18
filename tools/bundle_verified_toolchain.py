"""Bundle only the locally verified P0 tools for private SAF transfer; never credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import stat
import zipfile

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()

def bundle(source,output,profile,record):
    provenance=json.loads(profile.read_text())
    aapt=source/'bin/aapt2'
    if digest(aapt)!=provenance['aapt2']['archive']['sha256']:
        raise ValueError('aapt2_hash_mismatch')
    if not (source/'android-sdk/licenses/android-sdk-license').is_file():
        raise ValueError('previous_sdk_acceptance_missing')
    roots=[(source/'jdk-17.0.21+9','jdk'),*( (source/'android-sdk'/name,'sdk/'+name) for name in ('platforms','build-tools','licenses'))]
    files=[]
    for root,prefix in roots:
        resolved=root.resolve(strict=True)
        for path in sorted(root.rglob('*')):
            target=path.resolve(strict=True)
            if not target.is_relative_to(resolved):raise ValueError('source_link_escape')
            if path.is_symlink() and target.is_dir():raise ValueError('directory_link_unsupported')
            if target.is_file():files.append((path,prefix+'/'+path.relative_to(root).as_posix()))
            elif not target.is_dir():raise ValueError('source_not_regular')
    files.append((aapt,'bin/aapt2'))
    if output.exists():raise FileExistsError(output)
    output.parent.mkdir(parents=True,exist_ok=True)
    profile_bytes=(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n').encode()
    expanded=len(profile_bytes);count=1
    try:
        with output.open('xb') as target,zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as archive:
            for path,name in files:
                info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o700)<<16
                with path.open('rb') as stream,archive.open(info,'w') as entry:
                    shutil.copyfileobj(stream,entry,1024*1024)
                expanded+=path.stat().st_size;count+=1
            info=zipfile.ZipInfo('profile.json',date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o700)<<16
            archive.writestr(info,profile_bytes)
        result={'profileId':provenance['profileId'],'sha256':digest(output),'archiveBytes':output.stat().st_size,'expandedBytes':expanded,'entries':count,'javaHome':'/opt/android/jdk','sdkRoot':'/opt/android/sdk','aapt2':'/opt/android/bin/aapt2','sdkLicenseAcceptance':'same user accepted P0 SDK terms; existing license files copied unchanged','privateLocalTransferOnly':True,'provenance':provenance}
        if result['archiveBytes']>512*1024*1024 or expanded>1024*1024*1024 or count>20000:raise ValueError('bundle_limits_exceeded')
        result['filename']='Android-Build-Tools-'+result['sha256'][:8]+'.zip'
        record.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps({k:v for k,v in result.items() if k!='provenance'},ensure_ascii=False))
    except BaseException:
        output.unlink(missing_ok=True)
        raise

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('source','output','profile','record'):parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args();bundle(args.source,args.output,args.profile,args.record)
if __name__=='__main__':main()
