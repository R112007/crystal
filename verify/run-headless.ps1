# Crystal 修为系统验证器 —— headless 逻辑层（Windows / PowerShell）
#
#   verify\run-headless.ps1 [数据目录] [场景类] [场景参数...]
#
# 不传场景类时只跑启动器自检（确认 游戏 → 模组 → 修为系统 这条链是通的）。
#
# 做的事：
#   1) 编译启动器 verify\boot\crystalverify\*.java        → verify\build\boot
#   2) 编译场景类 verify\tests\**\*.java（没有就跳过）    → verify\build\scenario
#   3) 装数据目录：<模组 jar> → <数据目录>\mods\Crystal.jar，并把场景类注进这个 jar
#      （注进 mod jar 是为了让场景类和模组共用同一个 classloader、同一份静态状态）
#   4) 起 headless：java -cp "<游戏 jar>;<服务端 jar>;verify\build\boot" crystalverify.Boot <数据目录> <场景类> ...
#
# jar 默认取本机 Downloads 里的官方包，可用参数或同名环境变量覆盖：
#   -GameJar    （默认 $env:GAME_JAR 或 C:\Users\RHN\Downloads\Mindustry.jar，桌面包，不带 headless 后端）
#   -ServerJar  （默认 $env:SERVER_JAR 或 C:\Users\RHN\Downloads\server-release.jar，补 arc.backend.headless）
#   -ModJar     （默认 $env:MOD_JAR 或 build\libs\Crystal.jar，没有就退回 CrystalDesktop.jar）
#
# 数据目录默认 verify\build\data（verify\.gitignore 里已经忽略 build\）。
[CmdletBinding()]
param(
    [Parameter(Position = 0)][string]$Data = '',
    [Parameter(Position = 1)][string]$Scenario = '',
    [string]$GameJar = $env:GAME_JAR,
    [string]$ServerJar = $env:SERVER_JAR,
    [string]$ModJar = $env:MOD_JAR,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$ScenarioArgs = @()
)

$ErrorActionPreference = 'Stop'

function Fail([string]$message) {
    Write-Host "[verify] $message"
    exit 2
}

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$here = Join-Path $root 'verify'

if (-not $GameJar) { $GameJar = 'C:\Users\RHN\Downloads\Mindustry.jar' }
if (-not $ServerJar) { $ServerJar = 'C:\Users\RHN\Downloads\server-release.jar' }
if (-not $ModJar) {
    $ModJar = Join-Path $root 'build\libs\Crystal.jar'
    if (-not (Test-Path -LiteralPath $ModJar)) {
        $ModJar = Join-Path $root 'build\libs\CrystalDesktop.jar'
    }
}
if (-not $Data) { $Data = Join-Path $here 'build\data' }

if (-not (Test-Path -LiteralPath $GameJar)) { Fail "找不到游戏 jar: $GameJar" }
if (-not (Test-Path -LiteralPath $ServerJar)) { Fail "找不到服务端 jar: $ServerJar" }
if (-not (Test-Path -LiteralPath $ModJar)) { Fail "找不到模组 jar（先跑 .\gradlew --offline dex）: $ModJar" }

$bootOut = Join-Path $here 'build\boot'
$scenOut = Join-Path $here 'build\scenario'
$modsDir = Join-Path $Data 'mods'
New-Item -ItemType Directory -Force -Path $bootOut, $scenOut, $modsDir | Out-Null

Write-Host '[verify] 编译启动器...'
$bootSrc = @(Get-ChildItem -Path (Join-Path $here 'boot\crystalverify') -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
& javac -nowarn -encoding UTF-8 -cp "$GameJar;$ServerJar" -d $bootOut @bootSrc
if ($LASTEXITCODE -ne 0) { Fail '启动器编译失败' }

$scenSrc = @(Get-ChildItem -Path (Join-Path $here 'tests') -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
if ($scenSrc.Count -gt 0) {
    Write-Host "[verify] 编译场景类 $($scenSrc.Count) 个..."
    & javac -nowarn -encoding UTF-8 -cp "$ModJar;$GameJar" -d $scenOut @scenSrc
    if ($LASTEXITCODE -ne 0) { Fail '场景类编译失败' }
} else {
    Write-Host '[verify] verify\tests 下没有场景类，跳过（模组照装）'
}

# 装数据目录：先铺一份干净的 mod jar，再把刚编出来的场景类注进去
$installed = Join-Path $modsDir 'Crystal.jar'
Copy-Item -LiteralPath $ModJar -Destination $installed -Force

$classFiles = @(Get-ChildItem -Path $scenOut -Recurse -Filter *.class -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
if ($classFiles.Count -gt 0) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::Open($installed, 'Update')
    try {
        $existing = @($zip.Entries | ForEach-Object { $_.FullName })
        foreach ($f in $classFiles) {
            $entry = $f.Substring($scenOut.Length + 1).Replace('\', '/')
            if ($existing -contains $entry) { Write-Host "[verify] 跳过 jar 里已有的 $entry"; continue }
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f, $entry) | Out-Null
        }
    } finally {
        $zip.Dispose()
    }
    Write-Host "[verify] 注入 $($classFiles.Count) 个场景类 → $installed"
}

$classpath = "$GameJar;$ServerJar;$bootOut"
$bootArgs = @($Data)
if ($Scenario) { $bootArgs += $Scenario }
if ($ScenarioArgs) { $bootArgs += $ScenarioArgs }
$label = if ($Scenario) { $Scenario } else { '启动器自检' }

Write-Host "[verify] 跑 $label（$Data）"
& java -cp $classpath crystalverify.Boot @bootArgs
$code = $LASTEXITCODE

if ($code -eq 3) {
    # 上次跑挂会在 settings 里留下 mod-crystal-failed，之后模组根本不会被加载（测试会"假过"）
    Write-Host '[verify] crystal 没加载（多半是上次跑挂留下的 failed 标记），清掉 settings 重试'
    foreach ($name in @('settings.bin', 'settings_backup.bin')) {
        $f = Join-Path $Data $name
        if (Test-Path -LiteralPath $f) { Move-Item -LiteralPath $f -Destination "$f.bak-$PID" }
    }
    & java -cp $classpath crystalverify.Boot @bootArgs
    $code = $LASTEXITCODE
}

exit $code