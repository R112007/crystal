[CmdletBinding()]
param(
    [string]$File,
    [string]$Target = '我的手机',
    [switch]$Build,
    [switch]$NoSend
)

$ErrorActionPreference = 'Stop'

# 用法：
#   .\scripts\send-to-qq.ps1                 发送 build\libs\Crystal.jar 到“我的手机”
#   .\scripts\send-to-qq.ps1 -Build          先构建再发送
#   .\scripts\send-to-qq.ps1 -Target 123456789
#   .\scripts\send-to-qq.ps1 -NoSend         只准备附件，不点发送

Add-Type @'
using System;
using System.Runtime.InteropServices;

public static class QQWindow {
    [DllImport("user32.dll")]
    public static extern bool SetProcessDpiAwarenessContext(IntPtr value);

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int command);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);

    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);

    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    public static int[] FindSearchBox(
        byte[] pixels, int stride, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y += 2) {
            int runStart = -1;
            int bestStart = -1;
            int bestEnd = -1;

            for (int x = left; x < right; x += 2) {
                int offset = y * stride + x * 4;
                byte b = pixels[offset];
                byte g = pixels[offset + 1];
                byte r = pixels[offset + 2];

                if (r >= 43 && r <= 51 && g >= 43 && g <= 51 && b >= 43 && b <= 51) {
                    if (runStart < 0) {
                        runStart = x;
                    }
                } else {
                    if (runStart >= 0 && x - runStart > bestEnd - bestStart) {
                        bestStart = runStart;
                        bestEnd = x;
                    }
                    runStart = -1;
                }
            }

            if (runStart >= 0 && right - runStart > bestEnd - bestStart) {
                bestStart = runStart;
                bestEnd = right;
            }

            if (bestStart >= 0 && bestEnd - bestStart >= 120) {
                return new int[] { bestStart, bestEnd, y + 10 };
            }
        }
        return null;
    }

    public static int[] FindSendButton(
        byte[] pixels, int stride, int left, int top, int right, int bottom) {
        int bestWidth = 0;
        int bestX = 0;
        int bestY = 0;

        for (int y = top; y < bottom; y += 3) {
            int start = -1;

            for (int x = left; x < right; x += 3) {
                int offset = y * stride + x * 4;
                byte b = pixels[offset];
                byte g = pixels[offset + 1];
                byte r = pixels[offset + 2];
                bool blue = r <= 20 && g >= 80 && g <= 125 && b >= 175 && b <= 230;

                if (blue) {
                    if (start < 0) {
                        start = x;
                    }
                } else {
                    if (start >= 0 && x - start >= 180 && x - start > bestWidth) {
                        bestWidth = x - start;
                        bestX = (start + x) / 2;
                        bestY = y;
                    }
                    start = -1;
                }
            }
        }

        return bestWidth > 0 ? new int[] { bestX, bestY } : null;
    }
}
'@

[void][QQWindow]::SetProcessDpiAwarenessContext([IntPtr](-4))
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Write-Step([string]$Message) {
    Write-Host "[QQ] $Message"
}

function Get-MainWindow {
    $process = Get-Process QQ -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 } |
        Select-Object -First 1

    if ($null -eq $process) {
        throw '没有找到已登录的 QQ 主窗口，请先打开并登录 QQ。'
    }

    return $process
}

function Get-WindowBounds([IntPtr]$Handle) {
    $rect = New-Object QQWindow+RECT
    if (-not [QQWindow]::GetWindowRect($Handle, [ref]$rect)) {
        throw '读取 QQ 窗口位置失败。'
    }

    return [PSCustomObject]@{
        Left = $rect.Left
        Top = $rect.Top
        Right = $rect.Right
        Bottom = $rect.Bottom
        Width = $rect.Right - $rect.Left
        Height = $rect.Bottom - $rect.Top
    }
}

function Set-QQForeground([IntPtr]$Handle) {
    [void][QQWindow]::ShowWindow($Handle, 9)
    [void][QQWindow]::SetForegroundWindow($Handle)
    Start-Sleep -Milliseconds 500
}

function Invoke-Click([int]$X, [int]$Y) {
    [void][QQWindow]::SetCursorPos($X, $Y)
    Start-Sleep -Milliseconds 80
    [QQWindow]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [QQWindow]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
}

function Set-TextClipboard([string]$Text) {
    for ($i = 0; $i -lt 5; $i++) {
        try {
            [System.Windows.Forms.Clipboard]::SetText($Text)
            return
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }
    throw '写入剪贴板失败，可能有其它程序正在占用剪贴板。'
}

function Set-FileClipboard([string]$Path) {
    $files = New-Object System.Collections.Specialized.StringCollection
    [void]$files.Add($Path)

    for ($i = 0; $i -lt 5; $i++) {
        try {
            [System.Windows.Forms.Clipboard]::SetFileDropList($files)
            return
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }
    throw '写入文件剪贴板失败，可能有其它程序正在占用剪贴板。'
}

function Get-ScreenBitmap {
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bitmap = New-Object System.Drawing.Bitmap(
        $bounds.Width,
        $bounds.Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen(
            $bounds.Location,
            [System.Drawing.Point]::Empty,
            $bounds.Size)
    } finally {
        $graphics.Dispose()
    }
    return $bitmap
}

function Get-PixelBuffer($Bitmap) {
    $rect = New-Object System.Drawing.Rectangle 0, 0, $Bitmap.Width, $Bitmap.Height
    $data = $Bitmap.LockBits(
        $rect,
        [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $length = [Math]::Abs($data.Stride) * $Bitmap.Height
        $bytes = New-Object byte[] $length
        [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $length)
        return [PSCustomObject]@{
            Bytes = $bytes
            Stride = $data.Stride
        }
    } finally {
        $Bitmap.UnlockBits($data)
    }
}

function Find-SearchBox($Bitmap, $Window) {
    $left = [Math]::Max(0, $Window.Left + 20)
    $right = [Math]::Min($Bitmap.Width, $Window.Left + 620)
    $top = [Math]::Max(0, $Window.Top + 20)
    $bottom = [Math]::Min($Bitmap.Height, $Window.Top + 220)
    $buffer = Get-PixelBuffer $Bitmap
    $result = [QQWindow]::FindSearchBox(
        $buffer.Bytes, $buffer.Stride, $left, $top, $right, $bottom)

    if ($null -ne $result) {
        return [PSCustomObject]@{
            Left = $result[0]
            Right = $result[1]
            Top = $result[2] - 10
            Bottom = $result[2] + 10
            Width = $result[1] - $result[0]
            CenterX = [int](($result[0] + $result[1]) / 2)
            CenterY = $result[2]
        }
    }

    # 主题或字号变化时，用窗口高度推导的缩放比例兜底。
    $uiScale = [Math]::Max(0.75, $Window.Height / 870.0)
    $fallbackCenterX = [int]($Window.Left + 165 * $uiScale)
    $fallbackCenterY = [int]($Window.Top + 62 * $uiScale)
    $fallbackHalfWidth = [int](100 * $uiScale)
    $fallbackHalfHeight = [int](10 * $uiScale)
    return [PSCustomObject]@{
        Left = $fallbackCenterX - $fallbackHalfWidth
        Right = $fallbackCenterX + $fallbackHalfWidth
        Top = $fallbackCenterY - $fallbackHalfHeight
        Bottom = $fallbackCenterY + $fallbackHalfHeight
        Width = $fallbackHalfWidth * 2
        CenterX = $fallbackCenterX
        CenterY = $fallbackCenterY
    }
}

function Find-SendButton($Bitmap, $Window) {
    $left = [Math]::Max(0, $Window.Left)
    $right = [Math]::Min($Bitmap.Width, $Window.Right)
    $top = [Math]::Max(0, $Window.Top)
    $bottom = [Math]::Min($Bitmap.Height, $Window.Bottom)
    $buffer = Get-PixelBuffer $Bitmap
    $result = [QQWindow]::FindSendButton(
        $buffer.Bytes, $buffer.Stride, $left, $top, $right, $bottom)

    if ($null -ne $result) {
        return [PSCustomObject]@{
            CenterX = $result[0]
            CenterY = $result[1]
        }
    }
    return $null
}

function Wait-SendButton($Window, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $bitmap = Get-ScreenBitmap
        try {
            $button = Find-SendButton $bitmap $Window
            if ($null -ne $button) {
                return $button
            }
        } finally {
            $bitmap.Dispose()
        }
        Start-Sleep -Milliseconds 400
    }
    return $null
}

function Wait-SendButtonGone($Window, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $bitmap = Get-ScreenBitmap
        try {
            if ($null -eq (Find-SendButton $bitmap $Window)) {
                return $true
            }
        } finally {
            $bitmap.Dispose()
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($File)) {
    $File = Join-Path $projectRoot 'build\libs\Crystal.jar'
}

if ($Build) {
    Write-Step '构建模组...'
    & (Join-Path $projectRoot 'gradlew.bat') --offline dex
    if ($LASTEXITCODE -ne 0) {
        throw "构建失败，退出码 $LASTEXITCODE。"
    }
}

$filePath = (Resolve-Path -LiteralPath $File).Path
if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
    throw "找不到要发送的文件：$filePath"
}

$fileInfo = Get-Item -LiteralPath $filePath
Write-Step "文件：$($fileInfo.FullName) ($([Math]::Round($fileInfo.Length / 1MB, 2)) MB)"
Write-Step "目标：$Target"

$qq = Get-MainWindow
$handle = [IntPtr]$qq.MainWindowHandle
Set-QQForeground $handle
$window = Get-WindowBounds $handle

$bitmap = Get-ScreenBitmap
try {
    $search = Find-SearchBox $bitmap $window
} finally {
    $bitmap.Dispose()
}

Write-Step '打开 QQ 搜索...'
Invoke-Click $search.CenterX $search.CenterY
Start-Sleep -Milliseconds 400
[System.Windows.Forms.SendKeys]::SendWait('^a')
Set-TextClipboard $Target
Start-Sleep -Milliseconds 150
[System.Windows.Forms.SendKeys]::SendWait('^v')
Start-Sleep -Milliseconds 2200

# 搜索下拉里的第一项就是精确匹配的用户/设备。
$scale = [Math]::Max(0.75, $search.Width / 188.0)
$firstResultX = [int]($search.Left + 134 * $scale)
$firstResultY = [int]($search.Bottom + 110 * $scale)
Write-Verbose "搜索框: x=$($search.Left)..$($search.Right), y=$($search.Top)..$($search.Bottom)"
Write-Verbose "第一项: x=$firstResultX, y=$firstResultY, scale=$scale"
Write-Step '打开搜索结果第一项...'
Invoke-Click $firstResultX $firstResultY
Start-Sleep -Milliseconds 1500

Write-Step '粘贴文件附件...'
# 点击聊天输入区，确保 Ctrl+V 落在当前会话而不是搜索框。
$inputX = [int]($window.Left + $window.Width * 0.56)
$inputY = [int]($window.Top + $window.Height * 0.85)
Invoke-Click $inputX $inputY
Start-Sleep -Milliseconds 500
Set-FileClipboard $filePath
Start-Sleep -Milliseconds 200
[System.Windows.Forms.SendKeys]::SendWait('^v')

$sendButton = Wait-SendButton $window 8
if ($null -eq $sendButton) {
    throw '没有出现“发送给 ...”确认框，未发送。请确认目标会话已打开。'
}

if ($NoSend) {
    Write-Step '附件已准备好，按 -NoSend 要求停在确认框，未发送。'
    exit 0
}

Write-Step '点击发送...'
Invoke-Click $sendButton.CenterX $sendButton.CenterY

if (Wait-SendButtonGone $window 30) {
    Write-Step '已发送。'
    exit 0
}

throw '点击发送后确认框没有消失，请到 QQ 里检查发送状态。'
