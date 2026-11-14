@echo off
setlocal
set CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe
set FX=C:\Windows\Microsoft.NET\Framework64\v4.0.30319
set WINMD=C:\Windows\System32\WinMetadata
set SCRIPT_DIR=%~dp0
set EXE=%SCRIPT_DIR%BleTestBeacon.exe

"%CSC%" -nologo -out:"%EXE%" ^
  -r:"%FX%\System.Runtime.dll" ^
  -r:"%FX%\System.Runtime.InteropServices.WindowsRuntime.dll" ^
  -r:"%FX%\System.Runtime.WindowsRuntime.dll" ^
  -r:"%WINMD%\Windows.Foundation.winmd" ^
  -r:"%WINMD%\Windows.Devices.winmd" ^
  -r:"%WINMD%\Windows.Storage.winmd" ^
  "%SCRIPT_DIR%BleTestBeacon.cs"

if errorlevel 1 (
  echo Failed to compile BleTestBeacon.cs
  exit /b 1
)

"%EXE%" %*
