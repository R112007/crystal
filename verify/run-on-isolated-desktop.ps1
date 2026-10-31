param(
    [Parameter(Mandatory = $true)]
    [string] $DesktopName,

    [Parameter(Mandatory = $true)]
    [string] $Executable,

    [string] $Arguments = "",

    [Parameter(Mandatory = $true)]
    [string] $WorkingDirectory
)

$nativeSource = @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

public static class IsolatedDesktopRunner
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
    public static extern bool GetExitCodeProcess(IntPtr hProcess, out uint exitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);
}
'@

Add-Type -TypeDefinition $nativeSource

$startup = [IsolatedDesktopRunner+STARTUPINFO]::new()
$startup.cb = [Runtime.InteropServices.Marshal]::SizeOf(
    [type][IsolatedDesktopRunner+STARTUPINFO])
$startup.lpDesktop = "winsta0\$DesktopName"

$processInfo = [IsolatedDesktopRunner+PROCESS_INFORMATION]::new()
$commandLine = [Text.StringBuilder]::new(
    '"' + $Executable + '"' + $(if ($Arguments) { " $Arguments" } else { "" }))
$created = [IsolatedDesktopRunner]::CreateProcess(
    $Executable,
    $commandLine,
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

try {
    [IsolatedDesktopRunner]::WaitForSingleObject(
        $processInfo.hProcess,
        [uint32]::MaxValue) | Out-Null
    $exitCode = [uint32]0
    [IsolatedDesktopRunner]::GetExitCodeProcess(
        $processInfo.hProcess,
        [ref]$exitCode) | Out-Null
    if ($exitCode -ne 0) {
        throw "Isolated command exited with code $exitCode"
    }
}
finally {
    [IsolatedDesktopRunner]::CloseHandle($processInfo.hThread) | Out-Null
    [IsolatedDesktopRunner]::CloseHandle($processInfo.hProcess) | Out-Null
}
