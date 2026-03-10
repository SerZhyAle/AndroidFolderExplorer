#!/usr/bin/env pwsh
# start-shizuku.ps1 — Diagnose and start Shizuku service via ADB
# Usage:
#   .\scripts\start-shizuku.ps1               # auto-picks Android 14+ device
#   .\scripts\start-shizuku.ps1 -Serial <id>  # target a specific device

param([string]$Serial = "")

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ShizukuPkg = "moe.shizuku.privileged.api"

function Invoke-Adb {
    param([string[]]$Args)
    if ($script:DeviceSerial) {
        & adb.exe -s $script:DeviceSerial @Args
    } else {
        & adb.exe @Args
    }
}

# ── 1. Check ADB ─────────────────────────────────────────────────────────────
Write-Host "`n[1] Checking ADB..." -ForegroundColor Cyan
try {
    $version = & adb.exe version 2>&1 | Select-Object -First 1
    Write-Host "    $version" -ForegroundColor Gray
} catch {
    Write-Host "    ERROR: adb not found. Add Android SDK platform-tools to PATH." -ForegroundColor Red
    exit 1
}

# ── 2. Device discovery + Android version ────────────────────────────────────
Write-Host "`n[2] Connected devices:" -ForegroundColor Cyan

$rawDevices = & adb.exe devices | Select-Object -Skip 1 | Where-Object { $_ -match '\bdevice\b' }
if (-not $rawDevices) {
    Write-Host "    No device connected (or not authorized)." -ForegroundColor Red
    Write-Host "    -> Enable USB Debugging, accept the RSA prompt on device." -ForegroundColor Yellow
    exit 1
}

$deviceList = @()
foreach ($line in $rawDevices) {
    $devSerial = ($line -split '\s+')[0]
    $apiVer = & adb.exe -s $devSerial shell getprop ro.build.version.sdk 2>&1
    $osVer  = & adb.exe -s $devSerial shell getprop ro.build.version.release 2>&1
    $apiInt = [int]($apiVer -replace '[^\d]', '')
    $deviceList += [pscustomobject]@{ Serial = $devSerial; Api = $apiInt; OS = $osVer.Trim() }
    Write-Host ("    [{0}] Android {1} (API {2})" -f $devSerial, $osVer.Trim(), $apiInt) -ForegroundColor Gray
}

# ── 3. Select target device ───────────────────────────────────────────────────
Write-Host "`n[3] Selecting target device..." -ForegroundColor Cyan

if ($Serial) {
    $target = $deviceList | Where-Object { $_.Serial -eq $Serial } | Select-Object -First 1
    if (-not $target) {
        Write-Host "    Device '$Serial' not found." -ForegroundColor Red; exit 1
    }
} else {
    # Pick the device with the highest Android API — that's where Shizuku is most needed
    $target = $deviceList | Sort-Object Api -Descending | Select-Object -First 1
}
$script:DeviceSerial = $target.Serial
Write-Host ("    Selected: [{0}] Android {1} (API {2})" -f $target.Serial, $target.OS, $target.Api) -ForegroundColor Green

if ($target.Api -lt 34) {
    Write-Host "    NOTE: Android $($target.OS) — Shizuku is optional. SAF access should work." -ForegroundColor Yellow
}

# ── 4. Shizuku installed? ────────────────────────────────────────────────────
Write-Host "`n[4] Checking Shizuku installation..." -ForegroundColor Cyan
$installed = Invoke-Adb "shell","pm","list","packages",$ShizukuPkg 2>&1
if ($installed -notmatch $ShizukuPkg) {
    Write-Host "    Shizuku is NOT installed on this device." -ForegroundColor Red
    Write-Host ""
    Write-Host "    Install options:" -ForegroundColor Yellow
    Write-Host "      Play Store : https://play.google.com/store/apps/details?id=$ShizukuPkg" -ForegroundColor White
    Write-Host "      F-Droid    : https://f-droid.org/packages/$ShizukuPkg/" -ForegroundColor White
    Write-Host "      Website    : https://shizuku.rikka.app/" -ForegroundColor White
    Write-Host ""
    Write-Host "    After install, open Shizuku app once, then re-run this script." -ForegroundColor Yellow
    exit 1
}
Write-Host "    Installed: OK" -ForegroundColor Green

# ── 5. Locate libshizuku.so (Shizuku v13+) ──────────────────────────────────
# Shizuku v13 removed start.sh. The server is now started via libshizuku.so,
# a native ELF binary in the app's native library directory.
Write-Host "`n[5] Locating libshizuku.so (Shizuku v13 mechanism)..." -ForegroundColor Cyan

# Resolve the APK path on device
$apkPathRaw = Invoke-Adb "shell","pm path $ShizukuPkg" 2>&1
# pm path returns: "package:/data/app/.../base.apk"
$apkPath = ($apkPathRaw -replace '^package:','').Trim()

if (-not $apkPath) {
    Write-Host "    ERROR: Could not resolve APK path for $ShizukuPkg" -ForegroundColor Red
    exit 1
}
Write-Host "    APK: $apkPath" -ForegroundColor Gray

# The native lib dir is the same dir as APK, under lib/<abi>/
$apkDir = $apkPath -replace '/base\.apk$',''
$libPaths = @(
    "$apkDir/lib/arm64/libshizuku.so",
    "$apkDir/lib/arm64-v8a/libshizuku.so",
    "$apkDir/lib/x86_64/libshizuku.so",
    "$apkDir/lib/x86/libshizuku.so"
)

$libShizuku = $null
foreach ($p in $libPaths) {
    $exists = Invoke-Adb "shell","[ -f '$p' ] && echo yes || echo no" 2>&1
    if ($exists -match "yes") {
        $libShizuku = $p
        Write-Host "    Found: $p" -ForegroundColor Green
        break
    } else {
        Write-Host "    Not found: $p" -ForegroundColor Gray
    }
}

if (-not $libShizuku) {
    Write-Host "`n    libshizuku.so not found. Shizuku APK may be corrupted." -ForegroundColor Red
    Write-Host "    Try reinstalling Shizuku from https://shizuku.rikka.app/" -ForegroundColor Yellow
    exit 1
}

# ── 6. Check if already running ──────────────────────────────────────────────
Write-Host "`n[6] Checking Shizuku service status..." -ForegroundColor Cyan
$running = Invoke-Adb "shell","ps -A 2>/dev/null | grep shizuku_server || echo ''" 2>&1
if ($running -match "shizuku_server") {
    Write-Host "    Shizuku is already running!" -ForegroundColor Green
    Write-Host "    -> Open AndroidFolderExplorer and tap Retry to grant permission." -ForegroundColor Yellow
    exit 0
}

# ── 7. Start Shizuku ─────────────────────────────────────────────────────────
# v13: execute libshizuku.so directly (not via app_process or start.sh)
Write-Host "`n[7] Starting Shizuku via libshizuku.so..." -ForegroundColor Cyan
$result = Invoke-Adb "shell","$libShizuku --apk=$apkPath" 2>&1
if ($result) { Write-Host "    $result" -ForegroundColor Gray }

Start-Sleep -Seconds 3

# ── 8. Verify ────────────────────────────────────────────────────────────────
Write-Host "`n[8] Verifying..." -ForegroundColor Cyan
$verify = Invoke-Adb "shell","ps -A 2>/dev/null | grep shizuku_server || echo ''" 2>&1
if ($verify -match "shizuku_server") {
    Write-Host "    Shizuku started successfully!" -ForegroundColor Green
    Write-Host "    -> Open AndroidFolderExplorer and tap Retry." -ForegroundColor Yellow
} else {
    Write-Host "    Service did not start. Check logcat for details:" -ForegroundColor Red
    Write-Host "      adb -s $($target.Serial) shell logcat -d -s Shizuku:* | tail -30" -ForegroundColor White
    Write-Host "    Manual start command:" -ForegroundColor Yellow
    Write-Host "      adb -s $($target.Serial) shell '$libShizuku --apk=$apkPath'" -ForegroundColor White
}
