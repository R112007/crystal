param(
    [Parameter(Mandatory = $true)]
    [string] $DesktopName,

    [Parameter(Mandatory = $true)]
    [string] $Executable,

    [Parameter(Mandatory = $true)]
    [string] $WorkingDirectory,

    [switch] $KeepAlive
)

$nativeSource = @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

public static class IsolatedDesktopNative
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct STARTUPINFO
    {
        public int cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public int dwX;
        public int dwY;
        public int dwXSize;
        public int dwYSize;
        public int dwXCountChars;
        public int dwYCountChars;
        public int dwFillAttribute;
        public int dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct PROCESS_INFORMATION
    {
        public IntPtr hProcess;
        public IntPtr hThread;
        public int dwProcessId;
        public int dwThreadId;
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr CreateDesktop(
        string lpszDesktop,
        IntPtr lpszDevice,
        IntPtr pDevmode,
        int dwFlags,
        uint dwDesiredAccess,
        IntPtr lpsa);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool CloseDesktop(IntPtr hDesktop);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CreateProcess(
        string lpApplicationName,
        StringBuilder lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint WaitForSingleObject(IntPtr hHandle, uint dwMilliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);
}
'@

Add-Type -TypeDefinition $nativeSource

$desktop = [IsolatedDesktopNative]::CreateDesktop(
    $DesktopName,
    [IntPtr]::Zero,
    [IntPtr]::Zero,
    0,
    [uint32]0x10000000,
    [IntPtr]::Zero)

if ($desktop -eq [IntPtr]::Zero) {
    throw "CreateDesktop failed: $([ComponentModel.Win32Exception]::new(
        [Runtime.InteropServices.Marshal]::GetLastWin32Error()).Message)"
}

try {
    $startup = [IsolatedDesktopNative+STARTUPINFO]::new()
    $startup.cb = [Runtime.InteropServices.Marshal]::SizeOf(
        [type][IsolatedDesktopNative+STARTUPINFO])
    $startup.lpDesktop = "winsta0\$DesktopName"

    $processInfo = [IsolatedDesktopNative+PROCESS_INFORMATION]::new()
    $command = [Text.StringBuilder]::new('"' + $Executable + '"')
    $created = [IsolatedDesktopNative]::CreateProcess(
        $Executable,
        $command,
        [IntPtr]::Zero,
        [IntPtr]::Zero,
        $false,
        [uint32]0x00000400,
        [IntPtr]::Zero,
        $WorkingDirectory,
        [ref]$startup,
        [ref]$processInfo)

    if (-not $created) {
        throw "CreateProcess failed: $([ComponentModel.Win32Exception]::new(
            [Runtime.InteropServices.Marshal]::GetLastWin32Error()).Message)"
    }

    Write-Output "PID=$($processInfo.dwProcessId)"
    Write-Output "DESKTOP=$DesktopName"
    [Console]::Out.Flush()

    if ($KeepAlive) {
        [IsolatedDesktopNative]::WaitForSingleObject(
            $processInfo.hProcess,
            [uint32]4294967295) | Out-Null
    }

    [IsolatedDesktopNative]::CloseHandle($processInfo.hThread) | Out-Null
    [IsolatedDesktopNative]::CloseHandle($processInfo.hProcess) | Out-Null
}
finally {
    [IsolatedDesktopNative]::CloseDesktop($desktop) | Out-Null
}
