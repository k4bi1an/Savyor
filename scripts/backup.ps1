# PowerShell wrapper for Savyor backup
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

$PythonExe = if (Test-Path "$ProjectDir\.venv\Scripts\python.exe") {
    "$ProjectDir\.venv\Scripts\python.exe"
} elseif (Test-Path "$ProjectDir\venv\Scripts\python.exe") {
    "$ProjectDir\venv\Scripts\python.exe"
} else {
    "python"
}

Write-Host "Running Savyor database & media backup..." -ForegroundColor Cyan
& $PythonExe "$ScriptDir\backup.py" @args
