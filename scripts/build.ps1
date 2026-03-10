#!/usr/bin/env pwsh
# build.ps1 — Build debug and/or release APKs for AndroidFolderExplorer
# Usage:
#   .\scripts\build.ps1             # both debug + release
#   .\scripts\build.ps1 -Debug      # debug only
#   .\scripts\build.ps1 -Release    # release only

param(
    [switch]$Debug,
    [switch]$Release
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Configuration ────────────────────────────────────────────────────────────
$env:JAVA_HOME  = "C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_OPTS = "-Djava.io.tmpdir=$env:TEMP"

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$OutputDir   = Join-Path $ProjectRoot "app\build\outputs\apk"

# If neither flag given, build both
if (-not $Debug -and -not $Release) {
    $Debug   = $true
    $Release = $true
}

# ── Helpers ──────────────────────────────────────────────────────────────────
function Invoke-Gradle {
    param([string]$Task)
    Write-Host ""
    Write-Host "==> gradlew $Task" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & "$ProjectRoot\gradlew.bat" $Task
    $sw.Stop()
    if ($LASTEXITCODE -ne 0) {
        Write-Host "BUILD FAILED ($Task) — exit code $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Done in $([math]::Round($sw.Elapsed.TotalSeconds, 1))s" -ForegroundColor Green
}

function Show-Apk {
    param([string]$Variant)
    $dir = Join-Path $OutputDir $Variant
    if (Test-Path $dir) {
        Get-ChildItem $dir -Filter "*.apk" | ForEach-Object {
            $sizeMb = [math]::Round($_.Length / 1MB, 2)
            Write-Host "  $($_.Name)  ($sizeMb MB)  →  $($_.FullName)" -ForegroundColor Yellow
        }
    }
}

# ── Build ────────────────────────────────────────────────────────────────────
Push-Location $ProjectRoot
try {
    if ($Debug) {
        Invoke-Gradle "assembleDebug"
        Show-Apk "debug"
    }

    if ($Release) {
        Invoke-Gradle "assembleRelease"
        Show-Apk "release"
    }

    Write-Host ""
    Write-Host "All requested builds completed successfully." -ForegroundColor Green
}
finally {
    Pop-Location
}
