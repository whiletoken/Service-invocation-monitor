@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   Service Invocation Monitor - Packaging Plugin
echo ============================================================

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-plugin.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if %EXIT_CODE% NEQ 0 (
    echo [ERROR] Packaging failed with exit code: %EXIT_CODE%
)

exit /b %EXIT_CODE%
