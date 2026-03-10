param(
    [Parameter(Mandatory=$true)][string]$Path,
    [Parameter(Mandatory=$true)][string]$Target,
    [Parameter(Mandatory=$true)][string]$Description
)

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$row = "| $timestamp | $Path | $Target | $Description |"
$changelog = Join-Path $PSScriptRoot "..\dev\CHANGELOG.md"

if (-not (Test-Path $changelog)) {
    New-Item -ItemType File -Path $changelog -Force | Out-Null
    "# Dev Changelog`n`n| Timestamp | Path | Target | Description |`n|---|---|---|---|" | Set-Content $changelog
}

Add-Content -Path $changelog -Value $row
Write-Host "Logged: $row"
