"""Opt-in local Linux regression: failed build, repair, PTY cancellation, restart.

Uses only a new disposable template project; does not touch the fork app's UID,
credentials, selected user project, installed applications or Linux settings.
Requires the previously prepared local toolchain and Gradle cache.
"""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import threading
import time


def identity(pid):
    try:
        fields=Path(f'/proc/{pid}/stat').read_text().split(') ',1)[1].split()
        return None if fields[0]=='Z' else fields[19]
    except (FileNotFoundError,ProcessLookupError):
        return None


def main():
    root=Path(__file__).resolve().parent.parent
    run=root/'.superpowers/build-recovery-check'
    run.mkdir()  # Never overwrite a previous run or a user's project.
    project=run/'project';project.mkdir()
    for source in (root/'app/src/main/assets/android-sample').rglob('*'):
        if source.is_file():
            assert not source.is_symlink()
            target=project/source.relative_to(root/'app/src/main/assets/android-sample')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
    out=root/'docs/evidence/build-recovery';out.mkdir(exist_ok=True)
    base='/workspace/'+root.name
    tools=base+'/.superpowers/toolchain-import-rootfs/opt/android'
    guest_project=base+'/.superpowers/build-recovery-check/project'
    def command(task):
        return ['mc-linux','--','/usr/bin/env','JAVA_HOME='+tools+'/jdk',
            'ANDROID_HOME='+tools+'/sdk','ANDROID_SDK_ROOT='+tools+'/sdk',
            'GRADLE_USER_HOME='+base+'/.superpowers/sample-new-gradle-cache',
            tools+'/jdk/bin/java','-Xmx1024m','-classpath',guest_project+'/gradle/wrapper/gradle-wrapper.jar',
            'org.gradle.wrapper.GradleWrapperMain','-p',guest_project,task,'--offline',
            '--no-daemon','--max-workers=1','--console=plain',
            '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
            '-Pandroid.aapt2FromMavenOverride='+tools+'/bin/aapt2']
    results={'scope':'original-app local fixtures; target fork UI and AI retry not observed',
             'offline':True,'stages':[]}
    def save():
        (out/'result.json').write_text(json.dumps(results,indent=2)+'\n')
    def build(label,success):
        start=time.monotonic()
        with (out/(label+'.log')).open('w') as log:
            code=subprocess.run(command(':app:assembleDebug'),cwd=root.parent,
                stdout=log,stderr=subprocess.STDOUT,timeout=150).returncode
        text=(out/(label+'.log')).read_text()
        result={'stage':label,'exitCode':code,'elapsedSeconds':round(time.monotonic()-start,1)}
        results['stages'].append(result);save();print(json.dumps(result),flush=True)
        assert (code==0)==success,text[-2000:]
        if not success:assert 'MainActivity.java' in text and 'error:' in text
    source=project/'app/src/main/java/dev/mobilecodex/sample/MainActivity.java'
    original=source.read_bytes()
    source.write_bytes(original+b'\nINVALID_BUILD_RECOVERY_TEST\n')
    try:build('expected-compile-failure',False)
    finally:source.write_bytes(original)
    build('repaired-build',True)
    with (project/'build.gradle').open('a') as script:
        script.write('\ntasks.register("recoveryWait") { doLast { println "BUILD_CANCEL_READY:" + ProcessHandle.current().pid(); System.out.flush(); Thread.sleep(120000) } }\n')
    # The production transport owns TERM, forwards it to its process group, then KILLs.
    process=subprocess.Popen([sys.executable,str(root/'app/src/main/assets/rooms/terminal_io.py'),
        *command('recoveryWait')],cwd=root.parent,stdin=subprocess.PIPE,stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,text=True,bufsize=1)
    ready=threading.Event();state={};lines=[]
    def read():
        for line in process.stdout:
            lines.append(line)
            match=re.search(r'BUILD_CANCEL_READY:(\d+)',line)
            if match:
                pid=int(match.group(1));state.update(pid=pid,start=identity(pid));ready.set()
    reader=threading.Thread(target=read,daemon=True);reader.start()
    try:
        assert ready.wait(90),'Gradle cancellation fixture did not reach its task'
        assert state['start'] is not None,'Child PID identity unavailable'
        began=time.monotonic();process.terminate();code=process.wait(timeout=8);reader.join(3)
        elapsed=round(time.monotonic()-began,2)
        deadline=time.monotonic()+3
        while identity(state['pid'])==state['start'] and time.monotonic()<deadline:time.sleep(.05)
        child_gone=identity(state['pid'])!=state['start']
        result={'stage':'cancel-real-gradle','exitCode':code,'stopSeconds':elapsed,
                'ownedGradleTaskProcessGone':child_gone}
        results['stages'].append(result);save();print(json.dumps(result),flush=True)
        assert code!=0 and child_gone,'Gradle task survived production PTY cancellation'
    finally:
        if process.poll() is None:
            process.terminate()
            try:process.wait(timeout=5)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=3)
        # Only this fixture's recorded PID/start time can be cleaned up here.
        if state.get('start') is not None and identity(state['pid'])==state['start']:
            os.kill(state['pid'],signal.SIGKILL)
        reader.join(3)
        for stream in (process.stdin,process.stdout):
            if stream:
                try:stream.close()
                except BrokenPipeError:pass
        (out/'cancel-real-gradle.log').write_text(''.join(lines))
    build('after-cancel-build',True)
    results['passed']=True;save()


if __name__=='__main__':main()
