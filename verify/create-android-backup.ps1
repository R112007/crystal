param(
    [Parameter(Mandatory = $true)][string]$Package,
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Entry,
    [Parameter(Mandatory = $true)][string]$Output
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$work = Join-Path $root 'verify\build\android-backup-stage'
$app = Join-Path $work "apps\$Package"
$files = Join-Path $app 'f'
$destination = Join-Path $files $Entry
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
Copy-Item -LiteralPath $File -Destination $destination -Force
Set-Content -LiteralPath (Join-Path $app '_manifest') -NoNewline -Encoding UTF8 `
    -Value ('{"version":1,"packageName":"' + $Package + '"}')

$tar = Join-Path $work 'backup.tar'
if (Test-Path -LiteralPath $tar) {
    Remove-Item -LiteralPath $tar -Force
}
& tar.exe --format=ustar -cf $tar -C $work apps
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to create Android backup tar.'
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Output) | Out-Null
$stream = [IO.File]::Create($Output)
try {
    $header = [Text.Encoding]::ASCII.GetBytes("ANDROID BACKUP`n5`n1`nnone`n")
    $stream.Write($header, 0, $header.Length)
    $zlib = [IO.Compression.ZLibStream]::new(
        $stream,
        [IO.Compression.CompressionMode]::Compress,
        $true)
    try {
        $input = [IO.File]::OpenRead($tar)
        try {
            $input.CopyTo($zlib)
        } finally {
            $input.Dispose()
        }
    } finally {
        $zlib.Dispose()
    }
} finally {
    $stream.Dispose()
}

Write-Output $Output
