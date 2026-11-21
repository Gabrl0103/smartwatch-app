@echo off
rem Camina en el emulador la ruta que sigue Rumbo, para la demo (ver simulate_walk.py).
rem Opciones: --desvio  --sin-senal  --velocidad 1.4
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
set "PY="
where py >nul 2>nul && set "PY=py -3"
if not defined PY if exist "%~dp0..\backend\venv\Scripts\python.exe" set "PY=%~dp0..\backend\venv\Scripts\python.exe"
if not defined PY where python >nul 2>nul && set "PY=python"
if not defined PY (
    echo No encuentro Python 3. Instala Python desde python.org.
    pause
    exit /b 1
)
%PY% "%~dp0simulate_walk.py" %*
echo.
pause
