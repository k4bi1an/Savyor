@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Savyor - Instagram Vault

:: 1. Detect Python executable
set "PYTHON_EXE="
if exist "%~dp0.venv\Scripts\python.exe" (
    set "PYTHON_EXE=%~dp0.venv\Scripts\python.exe"
) else if exist "%~dp0venv\Scripts\python.exe" (
    set "PYTHON_EXE=%~dp0venv\Scripts\python.exe"
) else (
    where python >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set "PYTHON_EXE=python"
    )
)

if "%PYTHON_EXE%"=="" (
    echo.
    echo ========================================================
    echo  [ERROR] Python executable not found!
    echo  Please ensure Python or .venv is present in:
    echo  %~dp0
    echo ========================================================
    echo.
    pause
    exit /b 1
)

:: 2. Check if Savyor server is already running on port 8000
netstat -ano | findstr :8000 | findstr LISTENING >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  Savyor is already running at http://localhost:8000
    echo  Opening in your default browser...
    echo ========================================================
    echo.
    start http://localhost:8000
    ping -n 2 127.0.0.1 >nul
    exit /b 0
)

:: 3. Ensure frontend production build exists
if not exist "%~dp0frontend\dist\index.html" (
    echo [INFO] Frontend build not found. Building now...
    if exist "%~dp0frontend" (
        cd "%~dp0frontend"
        call npm run build
        cd /d "%~dp0"
    )
)

echo.
echo ========================================================
echo                 SAVYOR - INSTAGRAM VAULT
echo ========================================================
echo.
echo  Starting server at http://localhost:8000 ...
echo  Opening browser automatically in 2 seconds...
echo  (Leave this window open while using Savyor. Close it to stop.)
echo.

:: Launch browser in background after 2 seconds
start /b cmd /c "ping -n 3 127.0.0.1 >nul & start http://localhost:8000"

:: Start the Savyor server
"%PYTHON_EXE%" server.py

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Savyor exited unexpectedly (code: %ERRORLEVEL%).
    pause
)
