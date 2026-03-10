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
| Android 11–13 | 30–33 | `MANAGE_EXTERNAL_STORAGE` ("All files access") |
| Android 14+ | 34+ | [Shizuku](https://shizuku.rikka.app/) — privileged shell via `libshizuku.so` (uid 2000) |

---

## Setup Guide — Step by Step

### Android 9 / 10 (API 28–29)

> Standard storage permissions. No extra tools needed.

1. **Install the APK** — download from [Releases](../../releases) and enable "Install from unknown sources" in Settings → Security.
2. **Launch the app.**
3. A permission dialog appears — tap **Allow** to grant `READ_EXTERNAL_STORAGE`.
4. Done. `Android/data/` is accessible directly via the `File` API.

> **Android 10 note:** The app uses `requestLegacyExternalStorage`, so scoped storage restrictions do not apply.

---

### Android 11 / 12 / 13 (API 30–33)

> Requires `MANAGE_EXTERNAL_STORAGE` — the "All files access" special permission.

1. **Install the APK** — download from [Releases](../../releases).
2. **Launch the app.**
3. The app will show a dialog explaining why the permission is needed. Tap **Open Settings**.
4. Android opens **Settings → Special app access → All files access**. Find **AndroidFolderExplorer** and toggle it **ON**.
5. Return to the app (press Back or use the Recent Apps button).
6. The app reloads the current directory automatically.

> `Android/data/` and `Android/obb/` are now fully accessible.

---

### Android 14 / 15 / 16+ (API 34+)

> `MANAGE_EXTERNAL_STORAGE` alone cannot enumerate `Android/data` subfolders on these versions. **[Shizuku](https://shizuku.rikka.app/)** is required — a privileged shell bridge that runs without root.

#### Step 1 — Install Shizuku

Install from any source:

| Source | Link |
|---|---|
| Google Play | [play.google.com](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) |
| F-Droid | [f-droid.org](https://f-droid.org/packages/moe.shizuku.privileged.api/) |
| Official site | [shizuku.rikka.app](https://shizuku.rikka.app/) |

#### Step 2 — Start the Shizuku service

Choose **one** of the methods below. You only need to do this once per boot (or use auto-start via Wireless Debugging — see Method B).

**Method A — via ADB (requires a PC once)**

1. Enable **Developer Options**: Settings → About phone → tap *Build number* 7 times.
2. Enable **USB Debugging**: Developer Options → USB debugging → ON.
3. Connect phone to PC via USB.
4. On PC, open a terminal:

   **Windows (PowerShell) — recommended, uses the included script:**
   ```powershell
   .\scripts\start-shizuku.ps1
   ```

   **Windows / macOS / Linux — manual:**
   ```bash
   # Find the path on the device
   adb shell pm path moe.shizuku.privileged.api
   # Output: package:/data/app/~~XXXX==/moe.shizuku.privileged.api-YYYY==/base.apk

   # Replace "base.apk" with "lib/arm64/libshizuku.so" in the path and run it:
   adb shell /data/app/~~XXXX==/moe.shizuku.privileged.api-YYYY==/lib/arm64/libshizuku.so
   ```

   > The hash segments `~~XXXX==` and `-YYYY==` will be different on every device/install. Copy the exact value from `pm path` output.

5. Open the Shizuku app — status should show **Running**.

**Method B — via Wireless Debugging (no PC needed after first pairing)**

> Android 11+ only. This allows Shizuku to auto-start on its own using the Wireless ADB subsystem.

1. Enable **Developer Options** (same as above).
2. Enable **Wireless debugging**: Developer Options → Wireless debugging → ON.
3. Open the **Shizuku app** on the device.
4. Tap **"Pairing via Wireless debugging"**.
5. Follow the on-screen prompts — Shizuku pairs itself automatically.
6. Enable **"Start on Launch"** in Shizuku settings so it restarts after every reboot.

#### Step 3 — Grant permission to AndroidFolderExplorer

1. Launch **AndroidFolderExplorer**.
2. Navigate to `Android/data/` — a dialog appears.
3. Tap **Allow** on the Shizuku permission prompt.
4. Done — full directory access is now active.

> Permission is remembered until you uninstall either app.

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

---

## Инструкция по настройке (Русский)

### Android 9 / 10 (API 28–29)

> Стандартные разрешения хранилища. Дополнительные инструменты не нужны.

1. **Установите APK** — скачайте из раздела [Releases](../../releases), предварительно включив "Установка из неизвестных источников" в Настройки → Безопасность.
2. **Запустите приложение.**
3. Появится диалог запроса разрешений — нажмите **Разрешить** для `READ_EXTERNAL_STORAGE`.
4. Готово. Папка `Android/data/` доступна напрямую через File API.

> **Примечание для Android 10:** Приложение использует `requestLegacyExternalStorage`, поэтому ограничения Scoped Storage не применяются.

---

### Android 11 / 12 / 13 (API 30–33)

> Требуется `MANAGE_EXTERNAL_STORAGE` — специальное разрешение "Доступ ко всем файлам".

1. **Установите APK** — скачайте из раздела [Releases](../../releases).
2. **Запустите приложение.**
3. Приложение покажет диалог с объяснением, зачем нужно разрешение. Нажмите **Открыть настройки**.
4. Android откроет **Настройки → Приложения → Специальный доступ → Доступ ко всем файлам**. Найдите **AndroidFolderExplorer** и переключите в положение **ВКЛ**.
5. Вернитесь в приложение (кнопка Назад или Последние приложения).
6. Приложение автоматически перезагрузит текущую директорию.

> Папки `Android/data/` и `Android/obb/` теперь полностью доступны.

---

### Android 14 / 15 / 16+ (API 34+)

> На этих версиях `MANAGE_EXTERNAL_STORAGE` не позволяет просматривать содержимое `Android/data`. Требуется **[Shizuku](https://shizuku.rikka.app/)** — привилегированный shell-мост без рута.

#### Шаг 1 — Установка Shizuku

Установите из любого источника:

| Источник | Ссылка |
|---|---|
| Google Play | [play.google.com](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) |
| F-Droid | [f-droid.org](https://f-droid.org/packages/moe.shizuku.privileged.api/) |
| Официальный сайт | [shizuku.rikka.app](https://shizuku.rikka.app/) |

#### Шаг 2 — Запуск службы Shizuku

Выберите **один** из методов ниже. Это нужно сделать один раз после каждой перезагрузки (или используйте автозапуск через Беспроводную отладку — Метод Б).

**Метод А — через ADB (требуется ПК один раз)**

1. Включите **Параметры разработчика**: Настройки → О телефоне → нажимайте *Номер сборки* 7 раз.
2. Включите **Отладку по USB**: Параметры разработчика → Отладка по USB → ВКЛ.
3. Подключите телефон к ПК по USB.
4. На ПК откройте терминал:

   **Windows (PowerShell) — рекомендуется, использует готовый скрипт:**
   ```powershell
   .\scripts\start-shizuku.ps1
   ```

   **Windows / macOS / Linux — вручную:**
   ```bash
   # Найти путь на устройстве
   adb shell pm path moe.shizuku.privileged.api
   # Вывод: package:/data/app/~~XXXX==/moe.shizuku.privileged.api-YYYY==/base.apk

   # Заменить "base.apk" на "lib/arm64/libshizuku.so" и запустить:
   adb shell /data/app/~~XXXX==/moe.shizuku.privileged.api-YYYY==/lib/arm64/libshizuku.so
   ```

   > Хэш-сегменты `~~XXXX==` и `-YYYY==` уникальны для каждого устройства и установки. Скопируйте точное значение из вывода `pm path`.

5. Откройте приложение Shizuku — статус должен показывать **Работает**.

**Метод Б — через Беспроводную отладку (без ПК после первой настройки)**

> Только Android 11+. Позволяет Shizuku автоматически запускаться после перезагрузки.

1. Включите **Параметры разработчика** (как описано выше).
2. Включите **Беспроводную отладку**: Параметры разработчика → Беспроводная отладка → ВКЛ.
3. Откройте приложение **Shizuku** на устройстве.
4. Нажмите **«Сопряжение через беспроводную отладку»**.
5. Следуйте инструкциям на экране — Shizuku выполняет сопряжение автоматически.
6. Включите **«Запускать при старте»** в настройках Shizuku, чтобы служба запускалась после каждой перезагрузки.

#### Шаг 3 — Выдача разрешения приложению AndroidFolderExplorer

1. Запустите **AndroidFolderExplorer**.
2. Перейдите в `Android/data/` — появится диалог.
3. Нажмите **Разрешить** на запросе разрешения Shizuku.
4. Готово — полный доступ к директориям активирован.

> Разрешение сохраняется до удаления одного из приложений.

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
