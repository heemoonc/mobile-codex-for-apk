"""Prepare an isolated Android media probe with byte-identical production sources."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ['ImageStore.java', 'AttachmentStore.java', 'core/Json.java',
           'core/Utf8Files.java', 'core/Texts.java']


def prepare(template, output):
    template, output = Path(template).resolve(), Path(output).resolve()
    if output.exists():
        raise FileExistsError('Choose a new probe output directory; existing projects are never overwritten.')
    if not (template / 'gradle/wrapper/gradle-wrapper.jar').is_file():
        raise ValueError('A generated Java template with its Gradle wrapper is required.')
    shutil.copytree(template, output, ignore=shutil.ignore_patterns('build', '.gradle', '.kotlin'))
    java = output / 'app/src/main/java'
    shutil.rmtree(java)  # Only the newly copied, owned probe tree.
    target = java / 'dev/mobilecodex/app'
    hashes = {}
    for relative in SOURCES:
        source = ROOT / 'app/src/main/java/dev/mobilecodex/app' / relative
        dest = target / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        assert source.read_bytes() == dest.read_bytes()
        hashes[relative] = hashlib.sha256(dest.read_bytes()).hexdigest()
    shutil.copyfile(ROOT / 'tools/android-media-probe/MediaProbeActivity.java', target / 'MediaProbeActivity.java')
    (output / 'app/build.gradle').write_text('''plugins { id 'com.android.application' }
android {
    namespace 'dev.mobilecodex.app'
    compileSdk 35
    defaultConfig {
        applicationId 'com.example.workflow.mediaprobe'
        minSdk 29
        targetSdk 35
        versionCode 1
        versionName '1.0'
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
''')
    (output / 'app/src/main/AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<application android:label="Android 미디어 검증" android:allowBackup="false" android:theme="@android:style/Theme.Material.Light.NoActionBar">
<activity android:name="dev.mobilecodex.app.MediaProbeActivity" android:exported="true">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
</activity></application></manifest>
''')
    (output / 'production-sources.json').write_text(json.dumps(hashes, indent=2) + '\n')
    return hashes


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--template', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.template, args.output), indent=2))
