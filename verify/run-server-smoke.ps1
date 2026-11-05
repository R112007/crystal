[CmdletBinding()]
param(
    [string]$ModJar = '',
    [string]$ServerJar = '',
    [string]$AssetsJar = '',
    [string]$Data = '',
    [int]$TimeoutSeconds = 45
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$build = Join-Path $root 'verify\build'

if (-not $ModJar) {
    $ModJar = Join-Path $root 'build\libs\Crystal.jar'
}
if (-not $AssetsJar) {
    $AssetsJar = Join-Path $env:USERPROFILE 'Downloads\MindustryX.jar'
}
if (-not $ServerJar) {
    $ServerJar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\Anuken\Mindustry\latest" `
        -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Data) {
    $Data = Join-Path $build ("server-smoke-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}

foreach ($required in @($ModJar, $ServerJar, $AssetsJar)) {
    if (-not $required -or -not (Test-Path -LiteralPath $required)) {
        throw "Missing server validation dependency: $required"
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($ModJar)
try {
    if (-not ($zip.Entries | Where-Object { $_.FullName -eq 'classes.dex' })) {
        throw "Mod artifact does not contain classes.dex: $ModJar"
    }
} finally {
    $zip.Dispose()
}

$mods = Join-Path $Data 'config\mods'
New-Item -ItemType Directory -Force -Path $mods | Out-Null
Copy-Item -LiteralPath $ModJar -Destination (Join-Path $mods 'Crystal.jar') -Force
$stdout = Join-Path $Data 'server.stdout.log'
$stderr = Join-Path $Data 'server.stderr.log'

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    throw "JAVA_HOME does not contain java.exe: $env:JAVA_HOME"
}

$process = Start-Process -FilePath $java `
    -ArgumentList @('-cp', "$ServerJar;$AssetsJar", 'mindustry.server.ServerLauncher') `
    -WorkingDirectory $Data -WindowStyle Hidden `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$serverLoaded = $false
while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
    if ((Test-Path -LiteralPath $stdout) -and
        (Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue) -match 'Server loaded\.') {
        $serverLoaded = $true
        break
    }
    Start-Sleep -Milliseconds 500
}

$alive = -not $process.HasExited
if ($alive) {
    Stop-Process -Id $process.Id -Force
}

$output = ''
if (Test-Path -LiteralPath $stdout) {
    $output += Get-Content -LiteralPath $stdout -Raw
}
if (Test-Path -LiteralPath $stderr) {
    $output += Get-Content -LiteralPath $stderr -Raw
}

$loadedMod = $output -match 'Start to Loaded Crystal Mod Constructor' -and
    $output -match 'Have Loaded All Contents'
$hasLoadError = $output -match 'Error loading mod crystal'
$pass = $alive -and $serverLoaded -and $loadedMod -and -not $hasLoadError

Write-Host "[server] loaded=$loadedMod serverLoaded=$serverLoaded loadError=$hasLoadError"
Write-Host "[server] log=$stdout"
if (-not $pass) {
    Write-Host $output
    exit 1
}
Write-Host '[server] PASS'
exit 0
