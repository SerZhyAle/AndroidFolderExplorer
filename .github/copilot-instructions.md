# AndroidFolderExplorer — Copilot Instructions

## Project Purpose

Android file manager that exposes `Android/data/`, `Android/obb/`, and other restricted directories hidden since Android 11. **Not targeting Google Play.** Distributed as sideloaded APK only.

Users can browse restricted internal storage, select files/folders, and move or copy them to `Downloads`.

Supports **Android 9 (API 28)** and above.

---

## Fast Routing

| Task | Reference |
|---|---|
| Architecture / data flow | [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) |
| Build, flavors, signing | [docs/DEV_OPS.md](../docs/DEV_OPS.md) |
| Permissions & storage strategy | [docs/STORAGE_STRATEGY.md](../docs/STORAGE_STRATEGY.md) |
| Tech stack / libraries | [docs/TECH_STACK.md](../docs/TECH_STACK.md) |
| Dependency versions | [gradle/libs.versions.toml](../gradle/libs.versions.toml) |
| Task backlog | [task.md](../task.md) |

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Async | Kotlin Coroutines + Flow |
| DI | Hilt |
| Architecture | MVVM + Clean (Presentation → Domain → Data) |
| Build | Gradle KTS |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 |
| Serialization | kotlinx.serialization |

---

## Storage Access Strategy (CRITICAL)

This is the core complexity of the project. Always apply per-API-level:

### Android 9–10 (API 28–29)
- `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` granted at runtime.
- `File` API works directly on `/storage/emulated/0/Android/data/`.

### Android 11–13 (API 30–33)
- `MANAGE_EXTERNAL_STORAGE` via `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`.
- Check `Environment.isExternalStorageManager()` before file operations.
- `File` API accessible when permission granted.

### Android 14+ (API 34+)
- Same as API 30–33. `MANAGE_EXTERNAL_STORAGE` still respected.
- `MediaStore` does NOT cover `Android/data/` — use direct `File` access only after permission.

**Never** use `MediaStore` or `DocumentFile` (SAF) for `Android/data/` traversal — SAF requires per-folder user grants and cannot enumerate `Android/data/` subdirectories on Android 11+.

### Permission Check Pattern
```kotlin
fun hasFullStorageAccess(): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
    else -> ContextCompat.checkSelfPermission(ctx, READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED
}
```

---

## Architecture

```
app/
  src/main/
    presentation/          # Compose screens, ViewModels only
      browser/             # FileBrowserScreen, FileBrowserViewModel
      permissions/         # PermissionScreen, PermissionViewModel
    domain/                # Pure Kotlin, no Android deps (ideal)
      model/               # FileItem, CopyJob, MoveJob
      usecase/             # BrowseDirectoryUseCase, CopyFilesUseCase, MoveFilesUseCase
      repository/          # FileRepository (interface)
    data/
      repository/          # FileRepositoryImpl
      source/              # LocalFileDataSource (File API)
```

### Data Flow
`FileBrowserScreen` → `FileBrowserViewModel` → `BrowseDirectoryUseCase` → `FileRepositoryImpl` → `LocalFileDataSource`

---

## Key Implementation Rules

- **No UI logic in ViewModel** beyond state transformation.
- **All file I/O** runs on `Dispatchers.IO`. Never on Main.
- **Progress reporting** via `Flow<CopyProgress>` for copy/move operations.
- **Copy before delete** on move: verify destination write before removing source.
- `Downloads` target = `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`.
- Permission rationale UI **mandatory** before requesting `MANAGE_EXTERNAL_STORAGE`.

---

## Coding Standards

- **Zero lint warnings** policy.
- Comments: WHY, not WHAT.
- Max file size: 1000 lines; split if exceeded.
- All code, comments, logs: **English only**.
- No hardcoded paths — use `Environment` APIs.

---

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

---

## Common Pitfalls

1. **`File.listFiles()` returns null** on restricted dirs without `MANAGE_EXTERNAL_STORAGE` — always null-check and surface permission rationale.
2. **`ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` opens Settings**, not a dialog — handle `onResume` re-check.
3. **Android 10 scoped storage** (`WRITE_EXTERNAL_STORAGE` deprecated but still functional at API 29 with `requestLegacyExternalStorage` in manifest).
4. **Large directory copies** must be cancellable — use `CoroutineScope` with `Job` and honour `ensureActive()` in loops.
5. **Move = copy + delete** — never delete source before verifying destination file integrity (size check minimum).

---

## Manifest Flags

```xml
<!-- Required for Android 10 legacy access -->
<application android:requestLegacyExternalStorage="true" ...>

<!-- Permissions -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

---

## Response Language

- **Responses to user**: Russian.
- **All code, comments, logs, docs**: English.
