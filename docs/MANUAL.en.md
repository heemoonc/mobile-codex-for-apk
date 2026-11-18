# Mobile Codex for APK user manual

[Overview](../README.en.md) · [한국어 매뉴얼](MANUAL.ko.md) · [Latest APK](https://github.com/heemoonc/mobile-codex-for-apk/releases/latest)

This guide describes **0.3.9-apk-dev41**. Creating projects, preparing tools and building do not require ChatGPT or GitHub. AI conversations require an eligible account and internet access. Korean labels below match the installed UI; translated labels depend on language settings.

## 1. Install and update

Download `Mobile-Codex-for-APK-*.apk` from release Assets, not the automatic Source code archives. Update over the existing installation using the same package and signing key. Back up first: uninstalling may remove internal projects, tools and settings.

Application ID: `dev.mobilecodex.apk`. The original Mobile Codex is a separate app; accounts, Linux, tools and credentials are not shared automatically. The Accessibility entry is **Mobile Codex APK · 휴대폰 제어**. Confirm installation and permissions yourself in Android.

## 2. Prepare Android tools

Open **Tools → Android development setup** or **Android development start → Prepare development environment**.

1. Install and enable Linux.
2. Choose **2. 도구 다운로드·설치** (Download and install tools). Review sources, size and SDK terms, then accept explicitly.
3. Run the development-tool execution check and inspect its output. Existing files alone do not pass verification.
4. Create a project and build. Gradle and project dependencies download on first use.

Tool downloads are about 310 MiB and require at least 1500 MiB free space. Linux, Gradle and caches require additional storage. Downloads verify pinned sizes and SHA-256 hashes for Temurin JDK 17, Google SDK 35 / Build Tools 35.0.0 and Commit451 community ARM64 aapt2. The aapt2 binary is not an official Google ARM64 release. Existing `/opt/android` is preserved.

Defaults: JDK `/opt/android/jdk`, SDK `/opt/android/sdk`, aapt2 `/opt/android/bin/aapt2`. Existing custom project paths are not changed. Watch the spinner, status and execution log; stop if needed. Work may continue after closing a dialog, but restarting the app does not automatically resume it.

## 3. Prepare a project

Open **Android development start** from Home or Tools.

| Option | Behavior |
|---|---|
| New Android project | Choose name, package, folder and Java/Kotlin/Compose template. |
| Import from folder | Grant Android folder access, then copy into internal storage. The source is preserved; `.git` history is excluded. |
| Import source backup ZIP | Restore a source backup made by this app. Extract ordinary ZIPs to a folder before importing. |
| Import from GitHub | Enter `https://github.com/OWNER/REPOSITORY`, project name and folder. Public repositories can be cloned without authentication. |

New projects and imported copies live in app-private `android-projects`. Linux sees the same selected project files at `/workspace`; this is not a second source copy. The original **Choose working folder** feature remains available for Android-granted folders. Shared storage can restrict executables and symlinks, so prefer internal projects for Android builds.

Review imported build scripts before running them: they execute with the app's permissions. Project-specific build, verification and Git actions are disabled until a project is selected.

## 4. Implement features and tests

Connect an account for AI coding or use the file editor. Open **Android Build → Android development assistant** to inspect configuration, preview library/SDK changes and explicitly apply them. The catalog contains pinned verification profiles, not automatic latest-version recommendations. Complex Gradle and multi-module projects need manual review.

Write tests covering feature behavior, failure paths and persistence. Creating a Java template does not create feature-specific tests.

## 5. Stages and automatic verification

Open **Android development start → Development stages / automatic verification / manual**, or **Tools → Development progress & manual**.

- Stages show the next action. Code review and actual-use checkboxes are user reports.
- Automation runs environment checks → build/Lint/unit tests → optional API simulations. Standard tasks are `assembleDebug`, `lintDebug`, `testDebugUnitTest` for the `app` module.
- The manual tab explains each step and failure handling.

Failures, zero tests, skipped tests or missing execution evidence stop later stages. Check JUnit dependencies and `app/src/test`. Preview and apply simulation preparation in the assistant before selecting API simulations. Robolectric API 29–35 baseline checks do not replace actual OS, permissions, manufacturer or performance testing. The QEMU probe checks tools and machine initialization; it does not boot Android.

Reverify after source/tool changes. Automation does not implement code, change dependencies, install APKs or grant permissions. Changing project/account stops subsequent steps.

## 6. Build, export and install

1. Open **Build / save APK**, check project paths and Gradle task.
2. Run **Build APK**, then inspect terminal output and exit code.
3. Refresh outputs and inspect package, version, size and modification time.
4. Save the APK and confirm installation in Android yourself.
5. Check key features, rotation, reopening, persistence and network failures.

An existing APK or recent timestamp does not prove current-source build success or successful installation. A normal APK build does not prove unit tests passed.

## 7. Git history and GitHub upload

GitHub is optional and useful for history, backup, collaboration and releases.

1. Open **Android development start → Save changes / upload to GitHub** and refresh status.
2. Compare changes and select only intended files. Exclude credentials, personal data, caches and test-result documents.
3. Enter a message and confirm a local commit of selected files. This does not upload.
4. Separately confirm the remote, branch and commit before uploading. Upload sends the current branch's commits, including earlier history; review that history for secrets. No force push is used.

Only GitHub HTTPS remotes are supported by this screen. It does not create GitHub repositories or change visibility. Private repositories require account authentication and access. ChatGPT's GitHub connector, bundled Android Git and Linux Git have separate authentication.

New local projects without history need initialization, an origin and author settings. **This screen uses bundled Android Git: configure it through the app terminal with Linux disabled**, then re-enable Linux for builds.

```sh
git init -b main
git config user.name "Your Name"
git config user.email "YOUR_GITHUB_NOREPLY_EMAIL"
git remote add origin https://github.com/OWNER/REPOSITORY.git
```

Author settings are not authentication. Configure your own Git credential method separately; never place tokens or private keys in chats or source. Folder copies and source backup ZIPs exclude `.git`; clone from GitHub to retain Git history. A source ZIP does not back up the installed Linux environment, tools or credentials.

## 8. Troubleshooting

| Symptom | Action |
|---|---|
| Long-running operation | Check spinner, log and downloads; stop if necessary. |
| Git authentication/access error | Check repository access and bundled Git credentials; connector access alone is insufficient. |
| Push rejected | Inspect remote changes and history in the terminal; do not force push automatically. |
| Zero tests / NO-SOURCE | Add JUnit and actual test sources under `app/src/test`. |
| Older APK listed | Check build exit code and artifact metadata together. |
| Accessibility enabled but disconnected | Confirm the APK app entry; toggle it off/on yourself to reconnect. |
| Another API remains unverified | Current-device builds do not establish other-OS compatibility. |

The in-app manual is under **Development progress → User manual**. See [README](../README.en.md) and [third-party notices](../THIRD_PARTY_NOTICES.md) for source layout and licensing.

## Character packs

Character packs default to off on a fresh installation, with no character displayed. Enable them in settings if desired. Previously saved on/off preferences are preserved.
