#!/bin/sh
set -eu
here=$(CDPATH= cd "$(dirname "$0")" && pwd)
apis=${1:-29,35}
case "$apis" in ''|*[!0-9,]*|,*|*,|*,,*) echo 'Use comma-separated APIs 29-35' >&2; exit 2;; esac
old_ifs=$IFS; IFS=,
for api in $apis; do case "$api" in 29|30|31|32|33|34|35) ;; *) echo 'Supported APIs: 29-35' >&2; exit 2;; esac; done
IFS=$old_ifs
if [ "$#" -gt 0 ]; then shift; fi
export JAVA_HOME=${JAVA_HOME:-/opt/android/jdk}
export ANDROID_HOME=${ANDROID_HOME:-/opt/android/sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$JAVA_HOME/bin:$PATH"
aapt2=${AAPT2:-/opt/android/bin/aapt2}
test -x "$JAVA_HOME/bin/java" && test -x "$aapt2"
echo 'Library simulation only: not Android OS boot or full app compatibility proof.'
exec /bin/sh "$here/../../gradlew" -p "$here" :app:testDebugUnitTest --tests com.example.workflow.storageprobe.LibrarySimulationTest --rerun-tasks --no-daemon --max-workers=1 --console=plain "-PsimulationApis=$apis" "-Pandroid.aapt2FromMavenOverride=$aapt2" '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8' "$@"
