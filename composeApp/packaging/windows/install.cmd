@echo off
rem TS IPTV one-click setup. TS-IPTV-<version>-Setup.exe (packageDesktopRelease in
rem composeApp/build.gradle.kts) extracts this script and TS-IPTV.msi to a temporary folder and runs it.
rem Per-user MSI: no administrator prompt, installs to %LOCALAPPDATA%\TS IPTV.
setlocal
msiexec /i "%~dp0TS-IPTV.msi" /passive /norestart
set code=%errorlevel%
rem 0: installed. 3010: installed, restart pending. 1638: this version is already installed.
if "%code%"=="0" goto launch
if "%code%"=="3010" goto launch
if "%code%"=="1638" goto launch
exit /b %code%
:launch
start "" "%LOCALAPPDATA%\TS IPTV\TS IPTV.exe"
exit /b 0
