[CmdletBinding()]
param(
    [string]$DesktopJar = 'C:\Users\18287\Desktop\mdt\jre\desktop.jar',
    [string]$ServerJar = '',
    [string]$ModJar = '',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if (-not $ModJar) {
    $ModJar = Join-Path $root 'build\libs\Crystal.jar'
}
if (-not $SkipBuild) {
    & (Join-Path $root 'gradlew.bat') --offline check dex
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
if (-not $ServerJar) {
    $ServerJar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\Anuken\Mindustry\latest" `
        -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($ModJar)
try {
    if (-not ($zip.Entries | Where-Object { $_.FullName -eq 'classes.dex' })) {
        throw "Desktop artifact does not contain classes.dex: $ModJar"
    }
} finally {
    $zip.Dispose()
}

$runner = Join-Path $root 'verify\run-headless.ps1'
$data = Join-Path $root 'verify\build\desktop-validator'
foreach ($scenario in @('crystal.dbg.MobileCoreLaunchTest', 'crystal.dbg.SkillFrameworkTest')) {
    Write-Host "[desktop-client] $scenario"
    & $runner $data $scenario -GameJar $DesktopJar -ServerJar $ServerJar -ModJar $ModJar
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Write-Host '[desktop-server] launching headless server with mod'
& (Join-Path $root 'verify\run-server-smoke.ps1') -ModJar $ModJar -ServerJar $ServerJar
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host '[desktop] PASS (client scenarios + headless server)'
