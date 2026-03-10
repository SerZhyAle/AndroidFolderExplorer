# AndroidFolderExplorer

A file manager for Android that exposes `Android/data/`, `Android/obb/`, and other restricted directories hidden since Android 11.

> **Not available on Google Play.** Distributed as a sideloaded APK only.

---

## Features

- Browse `Android/data/`, `Android/obb/`, and all internal storage folders
- Multi-select files and folders
- Copy or move selected items to `Downloads`
- Works on **Android 9 through Android 14+** (API 28–35+)
- Progress reporting for long copy/move operations
- Cancellable operations

---

## Access Strategy

Android 11+ blocks direct `File` API access to `Android/data`. This app uses a tiered approach per API level:

| Android Version | API | Method |
|---|---|---|
| Android 9–10 | 28–29 | `File` API + `READ/WRITE_EXTERNAL_STORAGE` |
| Android 11–13 | 30–33 | Storage Access Framework (`MANAGE_EXTERNAL_STORAGE` + SAF picker) |
| Android 14+ | 34+ | [Shizuku](https://shizuku.rikka.app/) — privileged shell access (uid 2000) |

### Android 14+ — Shizuku Setup

On Android 14+, SAF can no longer enumerate `Android/data` subdirectories. Shizuku provides a shell-level bypass without root:

1. **Install Shizuku** — [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) · [F-Droid](https://f-droid.org/packages/moe.shizuku.privileged.api/) · [shizuku.rikka.app](https://shizuku.rikka.app/)
2. **Start the Shizuku service once** via ADB or Wireless ADB (Developer Options):
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. **Grant permission** when the app prompts you — one-time per install
4. After that, full `Android/data` access works without ADB

> Wireless ADB (Developer Options → Wireless debugging) allows pairing from the device itself — no PC required after the first setup.

---

## Download

Pre-built APKs are available on the [Releases](../../releases) page.

Latest: **AndroidFolderExplorer_v2-release.apk**

---

## Build from Source

Requirements:
- Android Studio Ladybug or newer
- JDK 17 (bundled with Android Studio)
- Android SDK 35

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (signed with debug keystore)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Lint
./gradlew lint
```

Output: `app/build/outputs/apk/release/AndroidFolderExplorer_v2-release.apk`

> The release build uses the Android debug keystore (`~/.android/debug.keystore`) for signing. To use a production keystore, update `signingConfigs` in `app/build.gradle.kts`.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Async | Kotlin Coroutines + Flow |
| DI | Hilt |
| Architecture | MVVM + Clean Architecture |
| Build | Gradle KTS |
| Privileged access | Shizuku 12.x (Android 14+) |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 |

---

## Architecture

```
app/src/main/java/com/afex/explorer/
├── presentation/
│   ├── browser/          # FileBrowserScreen, FileBrowserViewModel
│   └── permissions/      # Permission rationale UI
├── domain/
│   ├── model/            # FileItem, CopyJob, MoveJob
│   ├── usecase/          # BrowseDirectoryUseCase, CopyFilesUseCase, MoveFilesUseCase
│   └── repository/       # FileRepository (interface)
├── data/
│   ├── repository/       # FileRepositoryImpl — routes to correct access method
│   └── source/           # LocalFileDataSource, SafFileAccess, ShizukuFileAccess
└── di/                   # Hilt modules
```

Data flow: `Screen → ViewModel → UseCase → Repository → DataSource`

---

## Permissions

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
```

---

## Disclaimer

This app is intended for **personal use and advanced users** who want to manage their own device storage. It relies on privileged APIs and cannot be distributed through the Google Play Store.

---

## License

```
Copyright 2025-2026

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
