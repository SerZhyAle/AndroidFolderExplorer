# Dev Changelog

| Timestamp | Path | Target | Description |
|---|---|---|---|
| 2026-03-10 22:21:39 | project | initial | Full project scaffold: Gradle KTS, Hilt, Compose, MVVM+Clean. Screens: PermissionScreen, FileBrowserScreen. Domain: BrowseDirectory/Copy/Move UseCases. Data: FileRepositoryImpl + LocalFileDataSource. |
| 2026-03-11 00:10:22 | scripts/start-shizuku.ps1 | start-shizuku.ps1 | Fix: replace start.sh/app_process mechanism with libshizuku.so (Shizuku v13+ native binary). Find APK path via pm path, execute lib/arm64/libshizuku.so --apk= instead of sh start.sh. Correct process check: ps | grep shizuku_server. |
| 2026-03-11 00:10:22 | app/src/main/java/com/afex/explorer/presentation/browser/FileBrowserScreen.kt | FileBrowserScreen.kt | Fix: update ADB command shown in ShizukuNotRunning and ShizukuNotInstalled dialogs from deprecated start.sh to libshizuku.so via pm path sed command. |
| 2026-03-11 00:16:06 | README.md | README.md | Add detailed step-by-step setup guide for all Android versions (9-10, 11-13, 14+) in English and Russian. Replace outdated start.sh Shizuku command with correct libshizuku.so mechanism. Add Wireless Debugging method B for Shizuku auto-start. |
| 2026-03-11 00:55:48 | scripts/start-shizuku.ps1 | Invoke-Adb function | Renamed param from \ to \ — was conflicting with PowerShell automatic variable \, causing pm list packages to return empty output |
| 2026-03-11 01:01:23 | app/src/main/java/com/afex/explorer/presentation/browser/FileBrowserViewModel.kt | executeOperation | After Move completes: delay 1.2s then reload current directory so moved files disappear from the list |
| 2026-03-11 01:01:26 | app/src/main/java/com/afex/explorer/presentation/browser/FileBrowserViewModel.kt | executeOperation | After Move completes: delay 1.2s then reload current directory so moved files disappear from the list |
| 2026-03-11 01:23:56 | app/src/main/java/com/afex/explorer/data/repository/FileRepositoryImpl.kt | Move/Shizuku path selection and progress | Prefer Shizuku over MANAGE_EXTERNAL_STORAGE for restricted paths, snapshot Shizuku permission once per operation, add detailed move diagnostics and correct per-file progress accounting. |
| 2026-03-11 01:23:56 | app/src/main/java/com/afex/explorer/data/source/ShizukuFileAccess.kt | Shizuku shell diagnostics | Add stderr and exit-code logging for shell commands to diagnose mv/cp failures on restricted paths. |
| 2026-03-11 01:23:56 | app/src/main/java/com/afex/explorer/presentation/browser/FileBrowserViewModel.kt | Operation lifecycle and last path persistence | Persist/restore last opened folder via SharedPreferences and guard executeOperation against duplicate concurrent launches. |
| 2026-03-11 01:23:56 | app/src/main/java/com/afex/explorer/presentation/browser/FileBrowserScreen.kt | Browse list UX | Add LazyColumn vertical scrollbar indicator for long folder/file lists. |
