@echo off
setlocal EnableExtensions
title Smart Learning AI Service
cd /d "%~dp0"

set "PY_CMD="
set "PY312=%LocalAppData%\Programs\Python\Python312\python.exe"
if not defined EXTERNAL_LLM_MODEL set "EXTERNAL_LLM_MODEL=qwen3.7-max"
if not defined EXTERNAL_LLM_BASE_URL set "EXTERNAL_LLM_BASE_URL=https://ws-2gca4xhi5wbpqj12.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
set "USERPROFILE=%CD%\.runtime"
set "HOME=%CD%\.runtime"
set "XDG_CACHE_HOME=%CD%\.runtime\.cache"
set "PIP_CACHE_DIR=%CD%\pip\cache"
if not exist "%USERPROFILE%" mkdir "%USERPROFILE%"
if not exist "%XDG_CACHE_HOME%" mkdir "%XDG_CACHE_HOME%"
if not exist "%PIP_CACHE_DIR%" mkdir "%PIP_CACHE_DIR%"

if exist "%PY312%" (
    set "PY_CMD=%PY312%"
)

if not defined PY_CMD where py >nul 2>nul
if not defined PY_CMD if not errorlevel 1 (
    py -3.12 --version >nul 2>nul
    if not errorlevel 1 set "PY_CMD=py -3.12"
)

if not defined PY_CMD (
    where python >nul 2>nul
    if not errorlevel 1 set "PY_CMD=python"
)

if not defined PY_CMD goto no_python

echo [Smart Learning AI] Python command: %PY_CMD%
%PY_CMD% --version

%PY_CMD% -c "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 12) else 1)"
if errorlevel 1 goto bad_python

echo [Smart Learning AI] Checking dependencies...
%PY_CMD% -c "import fastapi, uvicorn; import main; print('Dependencies OK')"
if not errorlevel 1 goto start_service

echo [Smart Learning AI] Dependencies are missing or incomplete.
echo [Smart Learning AI] Installing dependencies once...
%PY_CMD% -m pip install -r requirements.txt
if errorlevel 1 goto pip_failed
%PY_CMD% -c "import fastapi, uvicorn; import main; print('Dependencies OK')"
if errorlevel 1 goto pip_failed

:start_service
echo [Smart Learning AI] Checking port 8000...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:8000/health; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>nul
if not errorlevel 1 goto already_running

echo [Smart Learning AI] Starting FastAPI service...
echo [Smart Learning AI] Health: http://127.0.0.1:8000/health
echo [Smart Learning AI] Docs:   http://127.0.0.1:8000/docs
if not defined EXTERNAL_LLM_API_KEY echo [Smart Learning AI] WARNING: EXTERNAL_LLM_API_KEY is not set. AI calls will fall back where possible.
echo [Smart Learning AI] Keep this window open while using AI features.
%PY_CMD% -m uvicorn main:app --host 127.0.0.1 --port 8000
goto end

:already_running
echo [Smart Learning AI] Service is already running.
echo [Smart Learning AI] Health: http://127.0.0.1:8000/health
echo [Smart Learning AI] Docs:   http://127.0.0.1:8000/docs
goto end

:no_python
echo [ERROR] Python was not found.
echo Install Python 3.12 first, then run this file again.
goto end

:bad_python
echo [ERROR] This AI service requires Python 3.12.
echo The current Python is shown above. Python 3.14 is not suitable for PaddleOCR/PaddlePaddle.
echo Install Python 3.12 from https://www.python.org/downloads/release/python-312/
echo Make sure "Add python.exe to PATH" is selected during installation.
goto end

:pip_failed
echo [ERROR] Dependency installation failed.
echo Check the Python version and network, then run this file again.
echo You can also run: %PY_CMD% -m pip install -r requirements.txt
goto end

:end
echo.
pause
