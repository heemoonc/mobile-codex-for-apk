<div align="center">

<img src="assets/readme/logo.svg" width="112" height="112" alt="Mobile Codex for APK" />

# Mobile Codex for APK

**An Android workspace for AI-assisted coding and APK development**

[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#requirements)
![Architecture](https://img.shields.io/badge/ABI-arm64--v8a-orange)
[![License](https://img.shields.io/badge/License-GPL--3.0--only-blue)](LICENSE)

[**Download latest APK**](https://github.com/heemoonc/mobile-codex-for-apk/releases/latest) · [All releases](https://github.com/heemoonc/mobile-codex-for-apk/releases)

[English](README.md) | [한국어](README.ko.md)

</div>

---

## Start here · dev41

[**User manual**](docs/MANUAL.en.md) · [한국어 매뉴얼](docs/MANUAL.ko.md) · [Current release: 0.3.9-apk-dev41](https://github.com/heemoonc/mobile-codex-for-apk/releases/tag/v0.3.9-apk-dev41)

**One Android device: prepare tools → create/import a project → implement → verify → export/install APK → optionally save to GitHub.**

| Your goal | Menu |
|---|---|
| First installation | Tools → Android development setup → Install/enable Linux → Download and install tools → Check execution |
| Create or import Android source | Home / Tools → Android development start → New project / Folder / Source backup ZIP / GitHub |
| See what to do next | Development stages / automatic verification / manual |
| Build and export | Build / save APK → Build APK → Refresh → Inspect → Save |
| Keep history and upload | Save changes / upload to GitHub → Compare → Select files → Commit → Separately confirm upload |

Projects created/imported through Android development start use app-private storage; Linux builds the same files through `/workspace`. The original working-folder picker remains available. **ChatGPT and GitHub are not required for project creation, setup or builds.** AI coding needs ChatGPT sign-in; private clones/uploads need separate bundled Git authentication. GitHub is optional.

dev41 defaults character packs to off on fresh installations. Development tools use a single download/install action. It includes the Android start hub and explicit Git clone/status/selected-file commit/upload screens. It also includes first-install tool downloads, progress indicators and the development workflow/manual. [Read the complete guide](docs/MANUAL.en.md).

## Overview

Mobile Codex for APK is an independent Android client for exploring projects, editing files, running commands and developing Android apps with Codex. It extends [Mobile Codex](https://github.com/nokryong/mobile-codex) for APK development on ARM64 devices. It is not an official OpenAI Android application.

Connect a project folder and use the chat workspace to move from a development request to source changes and build output. Python, Node.js and Git are bundled; the in-app development setup prepares Linux and the default Android build tools when needed.

> **Distribution status:** development build. AI inference and account authentication require an internet connection; the model does not run offline on the device. While the repository is private, downloads also require repository access.

## Features

| Feature | What it provides |
|---|---|
| AI coding workspace | Codex conversations, code explanations, edits, reviews and project conversations |
| Projects and files | Folder access, file browsing/editing, project import/export |
| Terminal and tools | Bundled Python, Node.js, npm, pip and Git |
| Android development | Java, Kotlin and Compose project templates; build-tool configuration; APK build, export and installation flows |
| Optional Linux | A PRoot-based Arch Linux ARM64 environment for Linux development commands |
| Phone control | Screen reading, taps, text input, scrolling and dragging after the user enables Accessibility and control |
| Voice | Voice input and Live audio, subject to account, service and permission availability |
| Appearance | Themes and character packs; character packs default to off on fresh installations |
| Tool approvals | Session or persistent approval choices when advertised by the tool request |

Library compatibility depends on Gradle, Kotlin, plugin versions and available device binaries. Compatibility with every Android library or desktop development tool is not guaranteed.

## Download the latest version

1. Open the **[latest release](https://github.com/heemoonc/mobile-codex-for-apk/releases/latest)**.
2. Under **Assets**, download `Mobile-Codex-for-APK-*.apk`. GitHub's automatic `Source code` archives are not installers.
3. Open the APK and confirm installation or update in Android's installer.
4. Optionally verify its SHA-256 against the release's `SHA256SUMS`.

| Item | Value |
|---|---|
| App name | Mobile Codex for APK |
| Android application ID | `dev.mobilecodex.apk` |
| Distribution | ARM64 APK, development build |
| Launcher icon | Orange background with code brackets and an APK badge |
| Updates | Install an APK with the same application ID and signing key over the existing app |

To retain app data, update without uninstalling. Builds signed with a different key cannot update an existing installation. Back up important projects before updating. This fork and the original Mobile Codex are separate apps; authentication and internal settings are not automatically shared.

<a id="requirements"></a>
## Requirements

- Android **10 / API 29 or later**, **ARM64 / `arm64-v8a`**.
- AI conversations require internet access and an account eligible to use Codex. Tool/dependency downloads also require internet; project creation and local builds do not require ChatGPT or GitHub sign-in.
- Free storage for projects, dependencies and build caches. Linux and Android tooling need additional space.
- A separate desktop or Termux application is not required. Prepare Linux and the default Android build tools inside the app when needed.

## Getting started

### 1. Prepare the environment inside the app

1. Open **Tools → Android development setup**. No account or project selection is required.
2. Run **Install/enable Linux → Download and install tools → Check execution**. Review sources and accept SDK terms to let the app prepare the default tools.
3. The app configures supported default tool paths. Existing custom project paths and plugins are not changed automatically.

### 2. From a project to an APK

1. Open **Android development start** on Home or Tools to create a Java/Kotlin/Compose project or import source. Grant Android folder access when importing from a folder.
2. Sign in for AI conversations, or work directly in the file editor.
3. Use **Development stages / automatic verification / manual** to find the next action and implement features and tests.
4. Review build/check results, save the APK and confirm installation in Android. Saving to GitHub is optional.

Android shared storage may restrict executables and symbolic links. Use the app's internal workspace for projects that need these features. Native npm/pip extensions and external tools require binaries or build environments compatible with Android or the selected Linux ARM64 environment.

### 3. Enable optional features

- **Phone control:** open Android Accessibility settings from Settings → Tools and enable access and control yourself. Use the on-screen stop button to end control. Screen content may be sent to the AI service; enable this only for the task you intend.

**Why enable phone control?** It lets Codex inspect the actual device screen and tap, type or scroll to help check the app you built. For example, Codex can help check whether a button responds, entered text appears, or an error occurs on a particular screen. It is optional for coding, environment setup and builds, and unnecessary if you prefer to operate the app yourself. You must still confirm APK installation and grant permissions yourself.

- **Voice:** allow microphone access when using voice features.
- **Character packs:** off by default on fresh installations. Enable a pack in settings if desired. No character is displayed when disabled.
- **GitHub:** connector repository access and terminal Git authentication are separate. Configure Git authentication in the environment running `git push`. Never paste tokens or private keys into chats or commit them.

## Android development assistant

Open **Android Build → Android development assistant** to:

1. Inspect declared SDK, AGP, Kotlin, Gradle and library settings, with unknown values and compatibility conditions clearly identified.
2. Preview exact library/SDK edits and apply them explicitly. Originals are backed up in app-private storage; stale previews are rejected.
3. Run build, Lint and unit checks together, or instrumentation tests on an already connected ADB device. Use the existing terminal for logs and Stop.
4. Track requested API levels separately as unverified, passed, failed, cancelled or stale after source changes. A local build does not prove every Android version works.

The library list is a **pinned verification profile** from the included probe projects, not a latest-version service or full Maven compatibility database. Automatic Kotlin-library edits use the checked `2.2.0` profile. Other versions, aliases, dynamic values and complex multi-module configurations require manual review. Selecting a library does not automatically upgrade SDKs or plugins.

**Optional advanced checks — external tests:** separately install ADB or Google Cloud CLI in Linux. ADB requires a device already connected and authorized by the user. Cloud execution requires existing `gcloud` authentication, an available Firebase Test Lab project/model, and may incur charges. Select project-relative app/test APK paths. Do not enter account secrets in the assistant.

Generate an external plan to copy commands. Imported JSON reports require `schema`, `kind`, `status`, `api`, `device`, `fingerprint` and `completedAt`. Imported results remain external reports. Direct ADB/cloud success verifies the selected APK artifacts and is distinguished from proof that they were built from the current source.

History stores source fingerprints and outcome summaries, not raw terminal logs. Fingerprinting excludes generated outputs/caches and has file-count, size and link safety limits; checks still run when a fingerprint cannot be obtained, with source association marked unverified. The ordinary APK build action remains separate.

## Package and repository layout

The Android application ID is `dev.mobilecodex.apk`; the Java source namespace is `dev.mobilecodex.app`. This distinction is intentional.

| Path | Purpose |
|---|---|
| `app/src/main/java/` | Android app, services, file providers and account/tool integration |
| `app/src/main/assets/web/` | WebView chat, settings and character rendering |
| `app/src/main/assets/android-sample/` | Base Android sample project |
| `app/src/main/assets/android-templates/` | Kotlin and Compose templates extending the Java-based sample |
| `app/src/main/assets/linux/` | Optional Linux configuration and launcher code |
| `app/src/main/res/` | Launcher/notification icons, themes, strings and other resources |
| `tools/` | Runtime/tool preparation, native builds, packaging and validation scripts |
| `tools/runtime-patches/` | Patches applied to the bundled Codex runtime |
| `packages/desktop-companion/` | Companion CLI for project transfer and synchronization |
| `tests/`, `app/src/test/`, `app/src/androidTest/` | UI, logic and Android test code |
| `icon-pack/` | Character-pack source assets |
| `gradle/`, `app/build.gradle` | Gradle Wrapper, dependencies and application build settings |

Generated `assets/runtime/`, `assets/devtools/`, `jniLibs/` and download caches are not tracked in Git. Prepared runtime components and tools are packaged in the APK. The optional Linux root filesystem is downloaded separately.

## Modify or build this app itself

install the released APK and open **Tools → Android development setup** to prepare Linux and the default JDK, SDK and aapt2. After you accept SDK terms and start installation/checks, the app handles downloads and default paths. Existing custom project settings are preserved. NDK and other tools for rebuilding this client’s native components are outside the default in-app setup.

For runtime packaging, native tools and signing when modifying or redistributing this client, follow the separate [contributor build guide](docs/BUILDING.en.md).

## Privacy and permissions

Project access follows the folder grants and execution permissions available to the app. Authentication, AI requests and optional screen/audio features can communicate with external services. Do not commit account data, tokens, private keys, signing keys or personal project files.

This repository includes app source and test code. Local credentials, test-result reports, device records, personal reference documents and automated deployment workflows are excluded from the distribution source.

## License and upstream

- App source: [GPL-3.0-only](LICENSE).
- Original project: [nokryong/mobile-codex](https://github.com/nokryong/mobile-codex).
- Component notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [`app/src/main/assets/legal/`](app/src/main/assets/legal/).
- Pinned Codex runtime, upstream and corresponding sources: [`tools/runtime-lock.json`](tools/runtime-lock.json).
- Pinned developer-tool versions, sources and checksums: [`tools/devtools-lock.json`](tools/devtools-lock.json).

OpenAI, Codex and other product names are trademarks of their owners. This fork is not an official or endorsed application from those companies.

## On-device per-API baseline simulation

- Open **Android Build → Android development assistant → Android version mock checks** and choose API 29–35.
- Preview and apply preparation to add JUnit, Robolectric 4.18 and two baseline tests. Automatic edits support only static, single-app Groovy `app/build.gradle` projects and stop on conflicting configuration.
- These tests check framework resources and a SharedPreferences round trip. They do not establish full app, real Android OS, permission, graphics or performance compatibility. Mock results are recorded separately from device evidence.
- The QEMU probe checks tools and TCG machine initialization in the current Linux environment only. It does not download an Android image or boot an OS. A missing QEMU installation is reported explicitly.
- Full OS emulation requires a compatible image and execution environment. Booting every Android version on the device is not provided by this feature.

Robolectric 4.18 supports the Linux ARM64 native runtime. Existing projects are not automatically upgraded. An available API selection does not guarantee execution support for a project; verify by running it.

Use the separate [library simulation project](tools/android-library-simulation/README.md) to repeat Room, DataStore and local HTTP checks. Run `sh tools/android-library-simulation/run.sh 29,35` in the Linux terminal. These results are not automatically merged into the baseline matrix.

Robolectric 4.18: https://github.com/robolectric/robolectric/releases/tag/robolectric-4.18


## Build an app using one Android device

1. Select **Projects + → Create Android app**, then choose the name, package, folder and Java/Kotlin/Compose template.
2. Open **Tools → Android build** and check Linux, JDK, SDK and aapt2 readiness. Confirm SDK terms and Android installation permissions yourself.
3. In **Android development assistant**, choose a library, preview the change and apply it. Complex existing Gradle configuration may require manual review.
4. Write the feature and tests through chat or the file editor, then run **Build, Lint and unit tests**.
5. Refresh APK outputs, inspect the package/version/modification time and save the APK. Distinguish fresh output from older artifacts.
6. Install the saved APK and check persistence, reopening and rotation on the device. Passing unit or simulation checks does not establish real OS compatibility.

The Java template includes an empty top-level `dependencies` block for library and simulation preparation. For Java projects created by earlier versions, review the build file and add `dependencies { }` at the top level if absent.

## Development progress and automatic verification

Open **Tools → Development progress & manual**, or **View development progress** in Android Build or the development assistant.

1. **Development stages:** Review project selection, code/libraries, environment, build/lint/unit tests, API simulations and APK installation/actual use together.
2. **Automatic verification:** Prepare a writable Android project and Linux, then start environment checks → build/lint/unit tests → selected API simulations. Simulations are off by default and require previewing/applying their setup in the assistant first.
3. **User manual:** Read environment setup, library changes, test authoring, APK export/installation and troubleshooting inside the app.

Automation runs `assembleDebug`, `lintDebug` and `testDebugUnitTest` for the standard `app` module. It reruns tests and checks actual counts. Zero tests, skipped tests, failures or missing execution evidence stop subsequent steps. For custom modules and variants, use the existing assistant or terminal with suitable project tasks.

Evidence is valid only for matching project source fingerprints and tool settings. Automation does not edit code/dependencies, install APKs, confirm permissions or operate the installed app. Manual checkboxes are user reports, not actual-device test records. API simulations do not replace testing on other real Android OS versions.

Use **Stop automation** or **View execution log** while running. Changing project/account stops subsequent steps. Reloading never resumes a run automatically. Opening the manual or refreshing progress does not execute project commands.

### Next actions and troubleshooting

The **Next action** card links to the appropriate menu for the current project and evidence. Create apps from **sidebar Projects + → Create Android app**, or open existing source with **Projects + → Connect local folder**. Ask in chat for feature and test implementation, then review required libraries in the assistant.

Ordinary unit tests need test sources under `app/src/test` and a JUnit dependency. If also using API simulations, **assistant → Preview mock check preparation → Apply previewed mock check preparation** adds JUnit, Robolectric and baseline tests. Baseline preparation does not write feature-specific tests.

When verification stops, **When verification stops** gives repair guidance for zero/skipped/failed tests or environment errors. Use **Open troubleshooting manual** alongside **View execution log**. Manual checkboxes remain user reports and do not automatically prove installation or actual use.

## First-install Android setup

Open **Tools → Android development setup**, or use **Start development setup** on the welcome screen. No project or ChatGPT login is required.

1. Install or enable the separate Linux environment (internet and storage required).
2. Choose **Download and install tools**, review sources and the full SDK terms, then explicitly accept to download and install. About 310 MiB downloads; at least 1500 MiB free space is required. Downloads use pinned sizes and SHA-256 hashes: Temurin JDK 17.0.21+9, Google SDK Platform 35 rev 2 / Build Tools 35.0.0 and Commit451 community ARM64 aapt2 (not an official Google ARM64 binary). Existing `/opt/android` is preserved. Stop or retry on failure; retries download again.
3. Verify Java, javac, SDK Platform 35, D8 and ARM64 aapt2 from fixed `/opt/android` paths in an isolated directory. A file listing alone does not pass this check.
4. Create an Android project, then build and test it. Gradle and project dependencies download at first build. Existing project configurations remain unchanged.

You may dismiss the welcome banner and reopen setup from Tools or Android Build. Nothing installs or executes merely by opening the app. Checks continue when the screen closes; an app restart requires a new check.
