param(
    [Parameter(Mandatory = $true)]
    [string] $DesktopName,

    [ValidateSet(
        "capture", "captureActive", "captureDialog", "captureSequence", "click", "clickActive", "clickForeground", "clickMany",
        "dragActive",
        "scrollActive", "skipDialogue", "typeActive", "typeDialog", "submitDialog",
        "submitDialogActive",
        "inspectDialog",
        "key", "keyActive")]
    [Parameter(Mandatory = $true)]
    [string] $Action,

    [int] $X = 0,
    [int] $Y = 0,
    [int] $EndX = 0,
    [int] $EndY = 0,
    [string] $Text = "",
    [int] $VirtualKey = 0,
    [int] $Count = 1,
    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

$typeResult = ""

$nativeSource = @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;

public static class IsolatedGameControlNative
{
    public delegate bool EnumProc(IntPtr hwnd, IntPtr value);

    public sealed class WindowInfo
    {
        public IntPtr Handle;
        public long ProcessId;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr OpenDesktop(
        string name, uint flags, bool inherit, uint access);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr OpenInputDesktop(
        uint flags, bool inherit, uint access);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SwitchDesktop(IntPtr desktop);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool EnumDesktopWindows(
        IntPtr desktop, EnumProc callback, IntPtr value);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool CloseDesktop(IntPtr desktop);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(
        IntPtr hwnd, StringBuilder text, int maxCount);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetClassName(
        IntPtr hwnd, StringBuilder text, int maxCount);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(
        IntPtr hwnd, out uint processId);

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hwnd, int command);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    public static extern bool BringWindowToTop(IntPtr hwnd);

    [DllImport("user32.dll")]
    public static extern IntPtr SetActiveWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    public static extern IntPtr SetFocus(IntPtr hwnd);

    [DllImport("kernel32.dll")]
    public static extern uint GetCurrentThreadId();

    [DllImport("user32.dll")]
    public static extern bool AttachThreadInput(
        uint idAttach, uint idAttachTo, bool attach);

    [DllImport("user32.dll")]
    public static extern IntPtr SendMessage(
        IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool PostMessage(
        IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool ScreenToClient(IntPtr hwnd, ref POINT point);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);

    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(
        uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);

    [DllImport("user32.dll")]
    public static extern void keybd_event(
        byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT
    {
        public int X;
        public int Y;
    }

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetProcessDPIAware();

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool SetWindowText(IntPtr hwnd, string text);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool EnumChildWindows(
        IntPtr parent, EnumProc callback, IntPtr value);

    public static WindowInfo FindMindustryWindow(string desktopName)
    {
        IntPtr desktop = OpenDesktop(desktopName, 0, false, 0x0001);
        if (desktop == IntPtr.Zero) return null;

        var found = new WindowInfo();
        try
        {
            EnumDesktopWindows(desktop, (hwnd, value) =>
            {
                var title = new StringBuilder(256);
                var className = new StringBuilder(256);
                uint processId;
                GetWindowText(hwnd, title, title.Capacity);
                GetClassName(hwnd, className, className.Capacity);
                GetWindowThreadProcessId(hwnd, out processId);
                if (className.ToString() == "SDL_app" &&
                    title.ToString().Contains("Mindustry"))
                {
                    found.Handle = hwnd;
                    found.ProcessId = processId;
                    return false;
                }
                return true;
            }, IntPtr.Zero);
        }
        finally
        {
            CloseDesktop(desktop);
        }
        return found.Handle == IntPtr.Zero ? null : found;
    }

    public static IntPtr FindWindowByTitle(
        string desktopName, string titlePart)
    {
        IntPtr desktop = OpenDesktop(desktopName, 0, false, 0x0001);
        if (desktop == IntPtr.Zero) return IntPtr.Zero;

        IntPtr found = IntPtr.Zero;
        try
        {
            EnumDesktopWindows(desktop, (hwnd, value) =>
            {
                var title = new StringBuilder(256);
                var className = new StringBuilder(256);
                GetWindowText(hwnd, title, title.Capacity);
                GetClassName(hwnd, className, className.Capacity);
                if (title.ToString().Contains(titlePart) ||
                    className.ToString() == "#32770")
                {
                    found = hwnd;
                    return false;
                }
                return true;
            }, IntPtr.Zero);
        }
        finally
        {
            CloseDesktop(desktop);
        }
        return found;
    }

    public static IntPtr FindChildByClass(IntPtr parent, string className)
    {
        IntPtr found = IntPtr.Zero;
        EnumChildWindows(parent, (hwnd, value) =>
        {
            var name = new StringBuilder(256);
            GetClassName(hwnd, name, name.Capacity);
            if (name.ToString() == className)
            {
                found = hwnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static IntPtr FindChildByClassBelow(
        IntPtr parent, string className, double relativeY)
    {
        RECT parentRect;
        if (!GetWindowRect(parent, out parentRect)) return IntPtr.Zero;

        int minimumY = parentRect.Top +
            (int)((parentRect.Bottom - parentRect.Top) * relativeY);
        IntPtr found = IntPtr.Zero;
        EnumChildWindows(parent, (hwnd, value) =>
        {
            var name = new StringBuilder(256);
            GetClassName(hwnd, name, name.Capacity);
            RECT rect;
            if (name.ToString() == className &&
                GetWindowRect(hwnd, out rect) &&
                rect.Top >= minimumY)
            {
                found = hwnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static string WindowText(IntPtr hwnd)
    {
        if (hwnd == IntPtr.Zero) return "";
        var text = new StringBuilder(1024);
        GetWindowText(hwnd, text, text.Capacity);
        return text.ToString();
    }

    public static string DescribeChildren(IntPtr parent)
    {
        var result = new StringBuilder();
        EnumChildWindows(parent, (hwnd, value) =>
        {
            var title = new StringBuilder(256);
            var className = new StringBuilder(256);
            GetWindowText(hwnd, title, title.Capacity);
            GetClassName(hwnd, className, className.Capacity);
            RECT rect;
            GetWindowRect(hwnd, out rect);
            result.Append("hwnd=").Append(hwnd)
                .Append(" class=").Append(className)
                .Append(" text=").Append(title)
                .Append(" rect=").Append(rect.Left).Append(',')
                .Append(rect.Top).Append(',').Append(rect.Right)
                .Append(',').Append(rect.Bottom).AppendLine();
            return true;
        }, IntPtr.Zero);
        return result.ToString();
    }

    public static IntPtr FindButtonBelowRight(
        IntPtr parent, double relativeY, double relativeX)
    {
        RECT parentRect;
        if (!GetWindowRect(parent, out parentRect)) return IntPtr.Zero;

        int minimumY = parentRect.Top +
            (int)((parentRect.Bottom - parentRect.Top) * relativeY);
        int minimumX = parentRect.Left +
            (int)((parentRect.Right - parentRect.Left) * relativeX);
        IntPtr found = IntPtr.Zero;
        EnumChildWindows(parent, (hwnd, value) =>
        {
            var name = new StringBuilder(256);
            GetClassName(hwnd, name, name.Capacity);
            RECT rect;
            if (name.ToString() == "Button" &&
                GetWindowRect(hwnd, out rect) &&
                rect.Top >= minimumY && rect.Left >= minimumX)
            {
                found = hwnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
}
'@

Add-Type -TypeDefinition $nativeSource
[IsolatedGameControlNative]::SetProcessDPIAware() | Out-Null

$windowInfo = [IsolatedGameControlNative]::FindMindustryWindow($DesktopName)
if ($null -eq $windowInfo) {
    throw "Mindustry window was not found on desktop '$DesktopName'."
}
$window = $windowInfo.Handle
$gameProcessId = $windowInfo.ProcessId

[IsolatedGameControlNative]::ShowWindow($window, 3) | Out-Null
[IsolatedGameControlNative]::BringWindowToTop($window) | Out-Null
[IsolatedGameControlNative]::SetForegroundWindow($window) | Out-Null
$targetThread = [IsolatedGameControlNative]::GetWindowThreadProcessId(
    $window, [ref]$gameProcessId)
$currentThread = [IsolatedGameControlNative]::GetCurrentThreadId()
[IsolatedGameControlNative]::AttachThreadInput(
    $currentThread, $targetThread, $true) | Out-Null
[IsolatedGameControlNative]::SetActiveWindow($window) | Out-Null
[IsolatedGameControlNative]::SetFocus($window) | Out-Null
Start-Sleep -Milliseconds 250

switch ($Action) {
    "typeDialog" {
        $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
            $DesktopName, "打开")
        if ($dialog -eq [IntPtr]::Zero) {
            $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                $DesktopName, "Open")
        }
        $edit = if ($dialog -ne [IntPtr]::Zero) {
            [IsolatedGameControlNative]::FindChildByClassBelow(
                $dialog, "Edit", 0.5)
        }
        else {
            [IntPtr]::Zero
        }
        $combo = if ($dialog -ne [IntPtr]::Zero) {
            [IsolatedGameControlNative]::FindChildByClassBelow(
                $dialog, "ComboBox", 0.5)
        }
        else {
            [IntPtr]::Zero
        }
        $typeResult = "dialog=$dialog edit=$edit"
        if ($edit -eq [IntPtr]::Zero -or
            -not [IsolatedGameControlNative]::SetWindowText($edit, $Text)) {
            $typeResult += " set=false"
            throw "Unable to set the file dialog path. $typeResult"
        }
        if ($combo -ne [IntPtr]::Zero) {
            [IsolatedGameControlNative]::SetWindowText($combo, $Text) | Out-Null
            $typeResult += " combo=$combo"
        }
        $typeResult += " set=true"
        $typeResult += " text=" + [IsolatedGameControlNative]::WindowText($edit)
    }
    "submitDialog" {
        $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
            $DesktopName, "打开")
        if ($dialog -eq [IntPtr]::Zero) {
            $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                $DesktopName, "Open")
        }
        $edit = if ($dialog -ne [IntPtr]::Zero) {
            [IsolatedGameControlNative]::FindChildByClassBelow(
                $dialog, "Edit", 0.5)
        }
        else {
            [IntPtr]::Zero
        }
        if ($edit -eq [IntPtr]::Zero) {
            throw "File dialog edit control was not found."
        }
        $openButton = [IsolatedGameControlNative]::FindButtonBelowRight(
            $dialog, 0.5, 0.5)
        if ($openButton -eq [IntPtr]::Zero) {
            throw "File dialog open button was not found."
        }
        [IsolatedGameControlNative]::SendMessage(
            $openButton, 0x00F5, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        [IsolatedGameControlNative]::SendMessage(
            $dialog, 0x0111, [IntPtr]1, $openButton) | Out-Null
        $typeResult = "dialog=$dialog edit=$edit button=$openButton submitted=true"
    }
    "submitDialogActive" {
        [IsolatedGameControlNative]::AttachThreadInput(
            $currentThread, $targetThread, $false) | Out-Null
        $gameDesktop = [IsolatedGameControlNative]::OpenDesktop(
            $DesktopName, 0, $false, 0x0100)
        $originalDesktop = [IsolatedGameControlNative]::OpenInputDesktop(
            0, $false, 0x0100)
        if ($gameDesktop -eq [IntPtr]::Zero -or
            $originalDesktop -eq [IntPtr]::Zero) {
            throw "Unable to open the active or isolated desktop."
        }
        try {
            if (-not [IsolatedGameControlNative]::SwitchDesktop($gameDesktop)) {
                throw "SwitchDesktop to '$DesktopName' failed."
            }
            Start-Sleep -Milliseconds 500
            $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                $DesktopName, "打开")
            if ($dialog -eq [IntPtr]::Zero) {
                $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                    $DesktopName, "Open")
            }
            $edit = if ($dialog -ne [IntPtr]::Zero) {
                [IsolatedGameControlNative]::FindChildByClassBelow(
                    $dialog, "Edit", 0.5)
            }
            else {
                [IntPtr]::Zero
            }
            $openButton = if ($dialog -ne [IntPtr]::Zero) {
                [IsolatedGameControlNative]::FindButtonBelowRight(
                    $dialog, 0.5, 0.5)
            }
            else {
                [IntPtr]::Zero
            }
            if ($edit -eq [IntPtr]::Zero -or $openButton -eq [IntPtr]::Zero) {
                throw "File dialog controls were not found."
            }
            $dialogProcessId = [uint32]0
            $dialogThread = [IsolatedGameControlNative]::GetWindowThreadProcessId(
                $dialog, [ref]$dialogProcessId)
            [IsolatedGameControlNative]::AttachThreadInput(
                $currentThread, $dialogThread, $true) | Out-Null
            [IsolatedGameControlNative]::SetForegroundWindow($dialog) | Out-Null
            [IsolatedGameControlNative]::SetActiveWindow($dialog) | Out-Null
            [IsolatedGameControlNative]::SetFocus($edit) | Out-Null
            [IsolatedGameControlNative]::keybd_event(
                [byte]0x0D, 0, 0, [UIntPtr]::Zero)
            [IsolatedGameControlNative]::keybd_event(
                [byte]0x0D, 0, 2, [UIntPtr]::Zero)
            [IsolatedGameControlNative]::SendMessage(
                $dialog, 0x0111, [IntPtr]1, $openButton) | Out-Null
            [IsolatedGameControlNative]::AttachThreadInput(
                $currentThread, $dialogThread, $false) | Out-Null
            $typeResult = "dialog=$dialog edit=$edit button=$openButton submittedActive=true"
            Start-Sleep -Milliseconds 500
        }
        finally {
            [IsolatedGameControlNative]::SwitchDesktop($originalDesktop) | Out-Null
            [IsolatedGameControlNative]::CloseDesktop($originalDesktop) | Out-Null
            [IsolatedGameControlNative]::CloseDesktop($gameDesktop) | Out-Null
        }
    }
    "inspectDialog" {
        $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
            $DesktopName, "打开")
        if ($dialog -eq [IntPtr]::Zero) {
            $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                $DesktopName, "Open")
        }
        if ($dialog -eq [IntPtr]::Zero) {
            throw "File dialog was not found."
        }
        $typeResult = [IsolatedGameControlNative]::DescribeChildren($dialog)
    }
    "click" {
        $rect = [IsolatedGameControlNative+RECT]::new()
        if (-not [IsolatedGameControlNative]::GetWindowRect($window, [ref]$rect)) {
            throw "GetWindowRect failed."
        }
        $point = [IsolatedGameControlNative+POINT]::new()
        $point.X = $rect.Left + $X
        $point.Y = $rect.Top + $Y
        [IsolatedGameControlNative]::ScreenToClient(
            $window, [ref]$point) | Out-Null
        $position = [IntPtr](($point.Y -shl 16) -bor ($point.X -band 0xFFFF))
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0006, [IntPtr]1, [IntPtr]::Zero) | Out-Null
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0007, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0200, [IntPtr]0, $position) | Out-Null
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0201, [IntPtr]1, $position) | Out-Null
        Start-Sleep -Milliseconds 80
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0202, [IntPtr]0, $position) | Out-Null
    }
    { $_ -in "clickActive", "clickForeground", "clickMany", "dragActive", "scrollActive", "skipDialogue", "typeActive", "keyActive" } {
        [IsolatedGameControlNative]::AttachThreadInput(
            $currentThread, $targetThread, $false) | Out-Null
        $gameDesktop = [IsolatedGameControlNative]::OpenDesktop(
            $DesktopName, 0, $false, 0x0100)
        $originalDesktop = [IsolatedGameControlNative]::OpenInputDesktop(
            0, $false, 0x0100)
        if ($gameDesktop -eq [IntPtr]::Zero -or
            $originalDesktop -eq [IntPtr]::Zero) {
            throw "Unable to open the active or isolated desktop."
        }
        try {
            if (-not [IsolatedGameControlNative]::SwitchDesktop($gameDesktop)) {
                throw "SwitchDesktop to '$DesktopName' failed."
            }
            # Desktop switches can take noticeably longer than one frame on
            # Windows. Give the game time to own the input desktop before
            # injecting mouse or keyboard events.
            Start-Sleep -Milliseconds 600
            if ($Action -notin @("clickForeground", "typeActive")) {
                [IsolatedGameControlNative]::SetForegroundWindow($window) | Out-Null
            }
            if ($Action -eq "keyActive") {
                for ($i = 0; $i -lt $Count; $i++) {
                    [IsolatedGameControlNative]::keybd_event(
                        [byte]$VirtualKey, 0, 0, [UIntPtr]::Zero)
                    [IsolatedGameControlNative]::keybd_event(
                        [byte]$VirtualKey, 0, 2, [UIntPtr]::Zero)
                    if ($i + 1 -lt $Count) {
                        Start-Sleep -Milliseconds 180
                    }
                }
            }
            elseif ($Action -eq "typeActive") {
                $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                    $DesktopName, "打开")
                if ($dialog -eq [IntPtr]::Zero) {
                    $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                        $DesktopName, "Open")
                }
                $edit = if ($dialog -ne [IntPtr]::Zero) {
                    [IsolatedGameControlNative]::FindChildByClassBelow(
                        $dialog, "Edit", 0.5)
                }
                else {
                    [IntPtr]::Zero
                }
                $typeResult = "dialog=$dialog edit=$edit"
                if ($edit -ne [IntPtr]::Zero -and
                    [IsolatedGameControlNative]::SetWindowText($edit, $Text)) {
                    $typeResult += " set=true"
                    [IsolatedGameControlNative]::SetForegroundWindow(
                        $dialog) | Out-Null
                }
                else {
                    $typeResult += " set=false"
                    Add-Type -AssemblyName System.Windows.Forms
                    [IsolatedGameControlNative]::SetCursorPos($X, $Y) | Out-Null
                    [IsolatedGameControlNative]::mouse_event(
                        0x0002, 0, 0, 0, [UIntPtr]::Zero)
                    Start-Sleep -Milliseconds 80
                    [IsolatedGameControlNative]::mouse_event(
                        0x0004, 0, 0, 0, [UIntPtr]::Zero)
                    Start-Sleep -Milliseconds 180
                    [System.Windows.Forms.SendKeys]::SendWait($Text)
                }
            }
            elseif ($Action -eq "scrollActive") {
                [IsolatedGameControlNative]::SetCursorPos($X, $Y) | Out-Null
                for ($i = 0; $i -lt [Math]::Abs($Count); $i++) {
                    $delta = if ($Count -gt 0) { 120 } else { -120 }
                    $wheelData = [uint32]([int64]$delta -band 4294967295)
                    [IsolatedGameControlNative]::mouse_event(
                        0x0800, 0, 0, $wheelData, [UIntPtr]::Zero)
                    Start-Sleep -Milliseconds 60
                }
            }
            elseif ($Action -eq "dragActive") {
                [IsolatedGameControlNative]::SetCursorPos($X, $Y) | Out-Null
                [IsolatedGameControlNative]::mouse_event(
                    0x0002, 0, 0, 0, [UIntPtr]::Zero)
                for ($step = 1; $step -le 12; $step++) {
                    $x = $X + [int](($EndX - $X) * ($step / 12.0))
                    $y = $Y + [int](($EndY - $Y) * ($step / 12.0))
                    [IsolatedGameControlNative]::SetCursorPos($x, $y) | Out-Null
                    Start-Sleep -Milliseconds 35
                }
                [IsolatedGameControlNative]::mouse_event(
                    0x0004, 0, 0, 0, [UIntPtr]::Zero)
            }
            else {
                [IsolatedGameControlNative]::SetCursorPos($X, $Y) | Out-Null
                $clicks = if ($Action -eq "skipDialogue") { 1 } else { $Count }
                for ($i = 0; $i -lt $clicks; $i++) {
                    [IsolatedGameControlNative]::mouse_event(
                        0x0002, 0, 0, 0, [UIntPtr]::Zero)
                    Start-Sleep -Milliseconds 80
                    [IsolatedGameControlNative]::mouse_event(
                        0x0004, 0, 0, 0, [UIntPtr]::Zero)
                    if ($i + 1 -lt $Count) {
                        Start-Sleep -Milliseconds 180
                    }
                }
            }
            if ($Action -eq "skipDialogue") {
                Start-Sleep -Milliseconds 450
                [IsolatedGameControlNative]::SetCursorPos(951, 560) | Out-Null
                [IsolatedGameControlNative]::mouse_event(
                    0x0002, 0, 0, 0, [UIntPtr]::Zero)
                Start-Sleep -Milliseconds 80
                [IsolatedGameControlNative]::mouse_event(
                    0x0004, 0, 0, 0, [UIntPtr]::Zero)
            }
            Start-Sleep -Milliseconds 180
        }
        finally {
            [IsolatedGameControlNative]::SwitchDesktop($originalDesktop) | Out-Null
            [IsolatedGameControlNative]::CloseDesktop($originalDesktop) | Out-Null
            [IsolatedGameControlNative]::CloseDesktop($gameDesktop) | Out-Null
        }
    }
    "key" {
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0100, [IntPtr]$VirtualKey, [IntPtr]1) | Out-Null
        [IsolatedGameControlNative]::SendMessage(
            $window, 0x0101, [IntPtr]$VirtualKey, [IntPtr]0xC0000001) | Out-Null
    }
    "captureDialog" {
        Add-Type -AssemblyName System.Drawing
        $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
            $DesktopName, "打开")
        if ($dialog -eq [IntPtr]::Zero) {
            $dialog = [IsolatedGameControlNative]::FindWindowByTitle(
                $DesktopName, "Open")
        }
        if ($dialog -eq [IntPtr]::Zero) {
            throw "File dialog was not found."
        }
        $rect = [IsolatedGameControlNative+RECT]::new()
        [IsolatedGameControlNative]::GetWindowRect($dialog, [ref]$rect) | Out-Null
        $width = $rect.Right - $rect.Left
        $height = $rect.Bottom - $rect.Top
        $bitmap = [System.Drawing.Bitmap]::new($width, $height)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $hdc = $graphics.GetHdc()
        try {
            [IsolatedGameControlNative]::PrintWindow($dialog, $hdc, 2) | Out-Null
        }
        finally {
            $graphics.ReleaseHdc($hdc)
            $graphics.Dispose()
        }
        $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Dispose()
        $typeResult = "dialog=$dialog captured=true"
    }
    "captureSequence" {
        Add-Type -AssemblyName System.Drawing
        $rect = [IsolatedGameControlNative+RECT]::new()
        [IsolatedGameControlNative]::GetWindowRect($window, [ref]$rect) | Out-Null
        $width = $rect.Right - $rect.Left
        $height = $rect.Bottom - $rect.Top
        $frames = if ($Count -gt 0) { $Count } else { 60 }
        for ($frame = 0; $frame -lt $frames; $frame++) {
            $bitmap = [System.Drawing.Bitmap]::new($width, $height)
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            $hdc = $graphics.GetHdc()
            try {
                [IsolatedGameControlNative]::PrintWindow(
                    $window, $hdc, 2) | Out-Null
            }
            finally {
                $graphics.ReleaseHdc($hdc)
                $graphics.Dispose()
            }
            $bitmap.Save(
                $OutputPath + "." + $frame.ToString("000") + ".png",
                [System.Drawing.Imaging.ImageFormat]::Png)
            $bitmap.Dispose()
            Start-Sleep -Milliseconds 500
        }
        $typeResult = "captured=$frames"
    }
    { $_ -in "capture", "captureActive" } {
        Add-Type -AssemblyName System.Drawing
        $rect = [IsolatedGameControlNative+RECT]::new()
        if (-not [IsolatedGameControlNative]::GetWindowRect($window, [ref]$rect)) {
            throw "GetWindowRect failed."
        }
        $width = $rect.Right - $rect.Left
        $height = $rect.Bottom - $rect.Top
        $bitmap = [System.Drawing.Bitmap]::new($width, $height)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $captured = $true
        $gameDesktop = [IntPtr]::Zero
        $originalDesktop = [IntPtr]::Zero
        if ($Action -eq "captureActive") {
            [IsolatedGameControlNative]::AttachThreadInput(
                $currentThread, $targetThread, $false) | Out-Null
            $gameDesktop = [IsolatedGameControlNative]::OpenDesktop(
                $DesktopName, 0, $false, 0x0100)
            $originalDesktop = [IsolatedGameControlNative]::OpenInputDesktop(
                0, $false, 0x0100)
            if ($gameDesktop -eq [IntPtr]::Zero -or
                $originalDesktop -eq [IntPtr]::Zero) {
                $graphics.Dispose()
                throw "Unable to open the active or isolated desktop."
            }
            if (-not [IsolatedGameControlNative]::SwitchDesktop($gameDesktop)) {
                [IsolatedGameControlNative]::CloseDesktop($originalDesktop) | Out-Null
                [IsolatedGameControlNative]::CloseDesktop($gameDesktop) | Out-Null
                $graphics.Dispose()
                throw "SwitchDesktop to '$DesktopName' failed."
            }
            Start-Sleep -Milliseconds 250
            try {
                $graphics.CopyFromScreen(
                    $rect.Left, $rect.Top, 0, 0,
                    [System.Drawing.Size]::new($width, $height))
            }
            finally {
                [IsolatedGameControlNative]::SwitchDesktop($originalDesktop) | Out-Null
                [IsolatedGameControlNative]::CloseDesktop($originalDesktop) | Out-Null
                [IsolatedGameControlNative]::CloseDesktop($gameDesktop) | Out-Null
            }
        }
        else {
            $hdc = $graphics.GetHdc()
            try {
                $captured = [IsolatedGameControlNative]::PrintWindow(
                    $window, $hdc, 2)
            }
            finally {
                $graphics.ReleaseHdc($hdc)
            }
        }
        $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Dispose()
        if (-not $captured) {
            throw "PrintWindow failed."
        }
    }
}

[IsolatedGameControlNative]::AttachThreadInput(
    $currentThread, $targetThread, $false) | Out-Null
Start-Sleep -Milliseconds 300
$windowRect = [IsolatedGameControlNative+RECT]::new()
[IsolatedGameControlNative]::GetWindowRect($window, [ref]$windowRect) | Out-Null
Set-Content -LiteralPath ($OutputPath + ".log") -Value (
    "window=$window pid=$gameProcessId rect=$($windowRect.Left)," +
    "$($windowRect.Top),$($windowRect.Right),$($windowRect.Bottom) " +
    "action=$Action x=$X y=$Y key=$VirtualKey count=$Count $typeResult")
