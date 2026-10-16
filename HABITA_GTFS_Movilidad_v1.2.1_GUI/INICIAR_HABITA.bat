@echo off
setlocal
cd /d "%~dp0"
title HABITA - Movilidad GTFS
echo.
echo HABITA / Movilidad GTFS / GUI 1.2.1
echo.
if exist ".venv\Scripts\python.exe" goto check_deps
where py >nul 2>nul
if not errorlevel 1 (
    py -3 -m venv .venv
) else (
    where python >nul 2>nul
    if errorlevel 1 goto missing_python
    python -m venv .venv
)
if errorlevel 1 goto error
:check_deps
".venv\Scripts\python.exe" -c "import sys; assert sys.version_info >= (3,10); import pandas, numpy; from zoneinfo import ZoneInfo; ZoneInfo('America/Santiago')" >nul 2>nul
if not errorlevel 1 goto launch
echo Instalando dependencias...
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto error
:launch
".venv\Scripts\python.exe" habita_gui.py
if errorlevel 1 goto error
exit /b 0
:missing_python
echo No se encontro Python. Instala Python 3.10 o superior desde python.org.
echo Activa la opcion Add Python to PATH durante la instalacion.
pause
exit /b 1
:error
echo.
echo No se pudo iniciar HABITA. Revisa el mensaje de arriba.
echo Se requiere Python 3.10 o superior. La primera instalacion requiere internet.
pause
exit /b 1
