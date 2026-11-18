# Mobile Codex for APK 자체 빌드 안내

[한국어 소개](../README.ko.md) · [English build guide](BUILDING.en.md)

이 문서는 Mobile Codex for APK 자체를 수정하고 재빌드하는 기여자용입니다. 일반 사용자가 자신의 Android 앱을 만드는 데 필요한 초기 설정 절차가 아닙니다.

일반 사용자는 배포 APK를 설치한 뒤 **도구 → Android 개발 환경 설정**에서 Linux와 기본 JDK·SDK·aapt2를 준비합니다. SDK 약관에 동의하고 설치·실행 확인을 시작하면 앱이 다운로드와 기본 경로 구성을 처리합니다. 기존 프로젝트의 사용자 지정 설정은 유지합니다. NDK 등 이 클라이언트 자체의 네이티브 빌드 도구는 기본 환경 설정에 포함되지 않습니다.

빌드용 JDK **17**, Android SDK **35**, Build Tools **35.0.0**, 네이티브 도구용 Android NDK **r28c**가 필요합니다. Gradle **8.13**과 Android Gradle Plugin **8.9.2**는 저장소 설정을 따릅니다. 소스만 복제한 상태에서는 런타임 준비 검사를 통과할 수 없습니다.

```sh
git clone https://github.com/heemoonc/mobile-codex-for-apk.git
cd mobile-codex-for-apk

# 스크립트별 옵션과 실행 환경을 먼저 확인합니다.
python3 tools/prepare_runtime.py --help
python3 tools/prepare_devtools.py --help
python3 tools/build_native.py --help

# 고정된 런타임·개발 도구를 준비합니다.
python3 tools/prepare_runtime.py
python3 tools/prepare_devtools.py
python3 tools/build_native.py --ndk "$ANDROID_NDK_HOME"

# ANDROID_HOME에 SDK 위치를 설정한 뒤 빌드합니다.
sh ./gradlew :app:assembleDebug
```

ARM64 Linux에서 빌드하는 경우 ARM64 호환 JDK·네이티브 빌드 도구·`aapt2`를 준비해야 합니다. 필요하면 `-Pandroid.aapt2FromMavenOverride=/path/to/aapt2`를 사용합니다. 일반 데스크톱용 Android SDK 바이너리가 ARM64에서 그대로 실행된다고 가정하지 마십시오.

- 디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`
- 서명 구성: `MOBILE_CODEX_KEYSTORE`, `MOBILE_CODEX_STORE_PASSWORD`, `MOBILE_CODEX_KEY_ALIAS`, `MOBILE_CODEX_KEY_PASSWORD` 환경 변수. 키는 저장소 밖에서 보관합니다.
- 진단 릴레이 기본 URL은 이 소스 사본에서 비워 두었습니다. 필요한 경우 별도로 구성합니다.
- UI 검사: 지원되는 Node.js 버전에서 `npm ci` 후 `npm test`. 지원 범위는 `package.json`의 `engines`를 확인하십시오.
- APK 재배포 시 `python3 tools/prepare_devtools_sources.py`로 생성한 개발 도구 대응 소스와 서드파티 고지를 함께 제공합니다.
