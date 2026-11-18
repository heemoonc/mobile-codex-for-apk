"""Local tablet-only P1 bootstrap compilation using the verified P0 tool profile."""
import argparse
import json
from pathlib import Path
import subprocess
import time

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--unit-tests',action='store_true',help='Also run upstream Android JVM tests; Linux ARM64 native runtime support is not assumed.')
args=parser.parse_args()
tasks=[':app:assembleBootstrap']+([':app:testBootstrapUnitTest'] if args.unit_tests else [])

root=Path(__file__).resolve().parent.parent
workspace=root.parent
jdk='/workspace/tabletforge-p0/toolchains/jdk-17.0.21+9'
sdk='/workspace/tabletforge-p0/toolchains/android-sdk'
project='/workspace/'+root.name
command=['mc-linux','--','/usr/bin/env','JAVA_HOME='+jdk,'ANDROID_HOME='+sdk,
         'ANDROID_SDK_ROOT='+sdk,'GRADLE_USER_HOME=/workspace/tabletforge-p0/work/gradle-home',
         jdk+'/bin/java','-Xmx1024m','-classpath',project+'/gradle/wrapper/gradle-wrapper.jar',
         'org.gradle.wrapper.GradleWrapperMain','-p',project,*tasks,
         '--no-daemon','--max-workers=1','--console=plain',
         '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
         '-Pandroid.aapt2FromMavenOverride=/workspace/tabletforge-p0/toolchains/bin/aapt2']
log=root/'docs/evidence/p1/build.log'
state=root/'docs/evidence/p1/build-job.json'
result={'status':'running','startedAt':time.time(),'exitCode':None,'finishedAt':None,
        'variant':'bootstrap','nativeRebuilt':False,'requestedTasks':tasks}
state.write_text(json.dumps(result,indent=2)+'\n')
code=2
try:
    with log.open('w') as output:
        code=subprocess.run(command,cwd=workspace,stdout=output,stderr=subprocess.STDOUT).returncode
    result.update(status='built' if code==0 else 'failed',exitCode=code)
except KeyboardInterrupt:
    code=130;result.update(status='interrupted',exitCode=code)
except OSError as error:
    result.update(status='failed',errorClass=type(error).__name__)
finally:
    result['finishedAt']=time.time();state.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
raise SystemExit(code)
