[CmdletBinding()]
param(
    [string]$Apk = '',
    [string]$ModJar = '',
    [string]$Avd = 'Medium_Phone_API_37.0',
    [int]$LaunchSeconds = 90,
    [switch]$StartEmulator,
    [switch]$Reinstall
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$build = Join-Path $root 'verify\build\android'
New-Item -ItemType Directory -Force -Path $build | Out-Null

$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
if (-not $Apk) {
    $Apk = Get-ChildItem (Join-Path $env:USERPROFILE 'Downloads') -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -ieq '.apk' } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $ModJar) {
    $ModJar = Join-Path $root 'build\libs\Crystal.jar'
}
foreach ($required in @($adb, $Apk, $ModJar)) {
    if (-not $required -or -not (Test-Path -LiteralPath $required)) {
        throw "Missing Android validation dependency: $required"
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($ModJar)
try {
    if (-not ($zip.Entries | Where-Object { $_.FullName -eq 'classes.dex' })) {
        throw "Android mod artifact does not contain classes.dex: $ModJar"
    }
} finally {
    $zip.Dispose()
}

function Get-Device {
    $lines = & $adb devices
    $line = $lines | Where-Object { $_ -match '^(\S+)\s+device' } | Select-Object -First 1
    if ($line -and $line -match '^(\S+)') {
        return $Matches[1]
    }
    return $null
}

$device = Get-Device
if (-not $device -and $StartEmulator) {
    $emuOut = Join-Path $build 'emulator.stdout.log'
    $emuErr = Join-Path $build 'emulator.stderr.log'
    Start-Process -FilePath $emulator -WindowStyle Hidden -RedirectStandardOutput $emuOut -RedirectStandardError $emuErr -ArgumentList @(
        '-avd', $Avd, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
        '-gpu', 'host', '-skin', '540x1200', '-dpi-device', '240', '-cores', '4', '-memory', '4096'
    )
    for ($i = 0; $i -lt 180; $i++) {
        $device = Get-Device
        if ($device) {
            $boot = & $adb -s $device shell getprop sys.boot_completed 2>$null
            if ($boot -and $boot.Trim() -eq '1') {
                break
            }
        }
        Start-Sleep -Seconds 2
    }
}
if (-not $device) {
    throw 'No Android device/emulator is available.'
}

& $adb -s $device shell settings put secure immersive_mode_confirmations confirmed
& $adb -s $device shell settings put global window_animation_scale 0
& $adb -s $device shell settings put global transition_animation_scale 0
& $adb -s $device shell settings put global animator_duration_scale 0

$installedPath = (& $adb -s $device shell pm path io.anuke.mindustry 2>$null | Out-String).Trim()
if ($Reinstall -or -not $installedPath) {
    Write-Host "[android] installing $Apk"
    & $adb -s $device install -r $Apk
    if ($LASTEXITCODE -ne 0) {
        throw 'APK installation failed.'
    }
} else {
    Write-Host '[android] APK already installed'
}

$remoteMods = '/sdcard/Android/data/io.anuke.mindustry/files/mods'
& $adb -s $device shell mkdir -p $remoteMods
& $adb -s $device push $ModJar "$remoteMods/Crystal.jar"
& $adb -s $device shell am force-stop io.anuke.mindustry
& $adb -s $device logcat -c
& $adb -s $device shell am start -n io.anuke.mindustry/mindustry.android.AndroidLauncher

$deadline = (Get-Date).AddSeconds($LaunchSeconds)
$logPath = Join-Path $build 'last_log.txt'
$modLoaded = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    & $adb -s $device pull '/sdcard/Android/data/io.anuke.mindustry/files/last_log.txt' $logPath 2>$null | Out-Null
    if ((Test-Path -LiteralPath $logPath) -and
        (Get-Content -LiteralPath $logPath -Raw) -match 'Start to Loaded Crystal Mod Constructor') {
        $modLoaded = $true
        break
    }
}

$gamePid = (& $adb -s $device shell pidof io.anuke.mindustry).Trim()
& $adb -s $device logcat -d -v brief | Set-Content -Path (Join-Path $build 'logcat.txt') -Encoding UTF8
$screen = Join-Path $build 'screen.png'
& $adb -s $device exec-out screencap -p > $screen

$remoteSize = [long]((& $adb -s $device shell stat -c %s "$remoteMods/Crystal.jar").Trim())
$localSize = (Get-Item -LiteralPath $ModJar).Length
$pass = $gamePid -and $remoteSize -eq $localSize -and $modLoaded
Write-Host "[android] pid=$gamePid modLoaded=$modLoaded remoteSize=$remoteSize localSize=$localSize"
Write-Host "[android] log=$logPath screen=$screen"
if (-not $pass) {
    exit 1
}
Write-Host '[android] PASS (APK install + mod injection + client load)'
