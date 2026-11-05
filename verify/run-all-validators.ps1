[CmdletBinding()]
param(
    [string]$Apk = '',
    [string]$ModJar = '',
    [switch]$StartEmulator
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not $ModJar) {
    $ModJar = Join-Path $root 'build\libs\Crystal.jar'
}

Write-Host '[validator] building desktop + classes.dex artifact'
& (Join-Path $root 'gradlew.bat') --offline check dex
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& (Join-Path $root 'verify\run-desktop-validator.ps1') -ModJar $ModJar -SkipBuild
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$androidArgs = @{ ModJar = $ModJar }
if ($Apk) { $androidArgs.Apk = $Apk }
if ($StartEmulator) { $androidArgs.StartEmulator = $true }
& (Join-Path $root 'verify\run-android-validator.ps1') @androidArgs
exit $LASTEXITCODE
