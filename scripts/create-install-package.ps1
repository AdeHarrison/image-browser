<#
.SYNOPSIS
    Packages everything needed for an offline Docker install onto a USB stick.

.DESCRIPTION
    Creates install-<YYYY-MM-DD-HH-MM-SS>\ at the repo root and copies into it:
      - docker-compose.yml, .env, install.txt
      - scripts\create-app-role.ps1 / .sh
      - data\input\ (the spreadsheet + source images; data\output is regenerated
        on the target machine by the app's own Reload, so it isn't copied)
      - image-browser-images.tar (docker save output; run "docker save postgres:16
        image-browser:latest -o image-browser-images.tar" at the repo root first)
      - "Docker Desktop Installer.exe" and "wsl_update_x64.msi" from the current
        user's Downloads folder

    Run from anywhere; the destination is always created next to this script's
    repo root, not the current directory.
#>

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Timestamp = Get-Date -Format 'yyyy-MM-dd-HH-mm-ss'
$DestDir = Join-Path $RepoRoot "install-$Timestamp"
$DownloadsDir = Join-Path $env:USERPROFILE 'Downloads'

$missing = @()

function Copy-RequiredFile($SourcePath, $DestDir) {
    if (Test-Path $SourcePath) {
        Copy-Item $SourcePath -Destination $DestDir -Force
    } else {
        $script:missing += $SourcePath
    }
}

function Copy-RequiredDir($SourcePath, $DestPath) {
    if (Test-Path $SourcePath) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $DestPath) | Out-Null
        Copy-Item $SourcePath -Destination $DestPath -Recurse -Force
    } else {
        $script:missing += $SourcePath
    }
}

New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

Copy-RequiredFile (Join-Path $RepoRoot 'docker-compose.yml') $DestDir
Copy-RequiredFile (Join-Path $RepoRoot '.env') $DestDir
Copy-RequiredFile (Join-Path $RepoRoot 'image-browser-images.tar') $DestDir
Copy-RequiredFile (Join-Path $RepoRoot 'install.txt') $DestDir

$ScriptsDestDir = Join-Path $DestDir 'scripts'
New-Item -ItemType Directory -Force -Path $ScriptsDestDir | Out-Null
Copy-RequiredFile (Join-Path $RepoRoot 'scripts\create-app-role.ps1') $ScriptsDestDir
Copy-RequiredFile (Join-Path $RepoRoot 'scripts\create-app-role.sh') $ScriptsDestDir

Copy-RequiredDir (Join-Path $RepoRoot 'data\input') (Join-Path $DestDir 'data\input')

Copy-RequiredFile (Join-Path $DownloadsDir 'Docker Desktop Installer.exe') $DestDir
Copy-RequiredFile (Join-Path $DownloadsDir 'wsl_update_x64.msi') $DestDir

Write-Host "Install package created at: $DestDir"

if ($missing.Count -gt 0) {
    Write-Warning "The following expected source files/dirs were not found and were skipped:"
    $missing | ForEach-Object { Write-Warning "  $_" }
}
