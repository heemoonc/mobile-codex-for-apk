#!/usr/bin/env python3
"""Opt-in real template builds on the tablet's prepared Linux toolchain.

Only creates fixtures in .superpowers; never installs APKs or changes user keys.
Run --stage templates, recovery, restore in that order using the same --run-name.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JDK='/workspace/tabletforge-p0/toolchains/jdk-17.0.21+9'
SDK='/workspace/tabletforge-p0/toolchains/android-sdk'
CACHE='/workspace/'+ROOT.name+'/.superpowers/sample-new-gradle-cache'
AAPT='/workspace/tabletforge-p0/toolchains/bin/aapt2'
PROFILES=('java','kotlin','compose')

def check_update(base,updated):
    if (base['package']!=updated['package'] or base['signer']!=updated['signer']
        or updated['versionCode']<=base['versionCode'] or base['sha256']==updated['sha256']):
        raise ValueError('Update identity, signer, version or APK contents mismatch')

def check_restore(before,restored):
    if any(before[k]!=restored[k] for k in ('package','versionCode','versionName','signer')):
        raise ValueError('Restored package/version/signing identity changed')

def check_failure(code,log):
    if code!=1 or 'MainActivity.java' not in log or 'error:' not in log:
        raise ValueError('Expected real compiler failure, not transport/environment failure')

def check_recompiled(log):
    lines={line.strip() for line in log.splitlines()}
    required={'> Task :app:compileDebugJavaWithJavac','> Task :app:packageDebug'}
    if not required.issubset(lines) or 'BUILD SUCCESSFUL' not in log:
        raise ValueError('Recovery must execute compilation and packaging, not only cached tasks')

def guest(path):
    return '/workspace/'+str(Path(path).resolve().relative_to(ROOT.parent))

def linux(args,log,success=True):
    with Path(log).open('w') as output:
        code=subprocess.run(['mc-linux','--','/usr/bin/env','JAVA_HOME='+JDK,
            'ANDROID_HOME='+SDK,'ANDROID_SDK_ROOT='+SDK,'GRADLE_USER_HOME='+CACHE,
            *map(str,args)],cwd=ROOT.parent,stdout=output,stderr=subprocess.STDOUT,timeout=600).returncode
    if success and code:raise RuntimeError(f'Command failed ({code}); inspect {log}')
    return code

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage',required=True,choices=['templates','recovery','restore'])
    parser.add_argument('--run-name',required=True)
    args=parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,48}',args.run_name):parser.error('Invalid fixture name')
    run=ROOT/'.superpowers'/('android-workflow-'+args.run_name)
    state=run/'results.json'
    if args.stage=='templates':
        run.mkdir() # Never reuse or overwrite an existing fixture.
        data={'profiles':{},'scope':'Linux on tablet; installation not automated','stage':'templates'}
    else:
        data=json.loads(state.read_text())
        required='templates' if args.stage=='recovery' else 'recovery'
        if data.get('completedStage')!=required:raise ValueError('Run stages in order, once each')
    def save():state.write_text(json.dumps(data,indent=2)+'\n')
    def build(profile,label,force=False):
        project=run/profile
        code=linux([JDK+'/bin/java','-Xmx512m','-classpath',guest(project/'gradle/wrapper/gradle-wrapper.jar'),
            'org.gradle.wrapper.GradleWrapperMain','-p',guest(project),':app:assembleDebug','--offline',
            '--no-daemon','--max-workers=1','--console=plain','-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
            '-Pandroid.aapt2FromMavenOverride='+AAPT,*(['--rerun-tasks'] if force else [])],run/(label+'.log'),success=False)
        print(label,'exit',code,flush=True)
        return code
    def apk(profile,label):
        file=run/profile/'app/build/outputs/apk/debug/app-debug.apk'
        if not file.is_file():raise ValueError('APK missing after successful build')
        badging=run/(label+'-badging.txt');cert=run/(label+'-certificate.txt')
        linux([AAPT,'dump','badging',guest(file)],badging)
        linux([JDK+'/bin/java','-jar',SDK+'/build-tools/35.0.0/lib/apksigner.jar','verify','--print-certs',guest(file)],cert)
        match=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging.read_text())
        signer=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})',cert.read_text())
        if not match or not signer:raise ValueError('APK identity/certificate missing')
        copies=run/'apks';copies.mkdir(exist_ok=True)
        snapshot=copies/(label+'.apk')
        payload=file.read_bytes()
        with snapshot.open('xb') as output:output.write(payload)
        return {'artifact':str(snapshot.relative_to(ROOT)), 'package':match[1],'versionCode':int(match[2]),'versionName':match[3],
            'signer':signer[1],'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'bytes':file.stat().st_size}
    if args.stage=='templates':
        classes=run/'classes';classes.mkdir()
        jars=list((ROOT.parent/'tabletforge-p0/work/gradle-home/caches/modules-2/files-2.1/org.json/json/20240303').rglob('*.jar'))
        if len(jars)!=1:raise ValueError('Expected prepared org.json 20240303 artifact')
        core=ROOT/'app/src/main/java/dev/mobilecodex/app/core'
        sources=[core/name for name in ['SampleProject.java','AndroidProject.java','SourceBackup.java','Json.java']]
        sources.append(ROOT/'tools/java-tests/AndroidWorkflowHarness.java')
        linux([JDK+'/bin/javac','-encoding','UTF-8','-cp',guest(jars[0]),'-d',guest(classes),*[guest(p) for p in sources]],run/'compile-harness.log')
        data['classpath']=guest(classes)+':'+guest(jars[0]);save()
        linux([JDK+'/bin/java','-cp',data['classpath'],'dev.mobilecodex.app.core.AndroidWorkflowHarness',guest(ROOT),guest(run),'create'],run/'generate.log')
        for profile in PROFILES:
            if build(profile,profile+'-base'):raise RuntimeError('Base build failed')
            base=apk(profile,profile+'-base')
            if base['package']!='com.example.workflow.'+profile+'fixture' or base['versionCode']!=1 or base['versionName']!='1.0':raise ValueError('Wrong generated APK')
            project=run/profile;p=project/'app/build.gradle'
            p.write_text(p.read_text().replace('versionCode 1','versionCode 2').replace("versionName '1.0'","versionName '2.0'"))
            p=project/'app/src/main/res/values/strings.xml';p.write_text(p.read_text().replace('Workflow '+profile+' 1','Workflow '+profile+' 2'))
            if build(profile,profile+'-update'):raise RuntimeError('Update build failed')
            updated=apk(profile,profile+'-update');check_update(base,updated)
            data['profiles'][profile]={'base':base,'update':updated};save()
    elif args.stage=='recovery':
        source=run/'java/app/src/main/java/com/example/workflow/javafixture/MainActivity.java';original=source.read_bytes()
        try:
            source.write_bytes(original+b'\nINTENTIONAL_WORKFLOW_COMPILE_FAILURE\n')
            code=build('java','expected-compile-failure');check_failure(code,(run/'expected-compile-failure.log').read_text())
        finally:source.write_bytes(original)
        if build('java','repaired',force=True):raise RuntimeError('Repaired build failed')
        check_recompiled((run/'repaired.log').read_text())
        repaired=apk('java','repaired');check_restore(data['profiles']['java']['update'],repaired)
        data['recovery']={'expectedFailureExit':code,'repaired':repaired};save()
    else:
        linux([JDK+'/bin/java','-cp',data['classpath'],'dev.mobilecodex.app.core.AndroidWorkflowHarness',guest(ROOT),guest(run),'restore'],run/'restore.log')
        for profile in PROFILES:
            if build(profile+'-restored',profile+'-restored'):raise RuntimeError('Restored build failed')
            restored=apk(profile+'-restored',profile+'-restored');check_restore(data['profiles'][profile]['update'],restored)
            data['profiles'][profile]['restored']=restored;save()
    data['completedStage']=args.stage;save();print('PASS',args.stage,flush=True)
if __name__=='__main__':main()
