@echo off
chcp 65001 >nul
title Pimcore Auto Tool (Windows Portable)
cls

cd /d "%~dp0"

echo ==============================================================================
echo                      PIMCORE AUTO TOOL - ENTERPRISE v2.0
echo             Tu dong hoa: Google Docs - Pimcore CMS - Web App Script
echo ==============================================================================
echo.

set "JAVACMD="
if exist "%~dp0jre\bin\javaw.exe" set "JAVACMD=%~dp0jre\bin\javaw.exe"
if "%JAVACMD%"=="" if exist "%~dp0runtime\bin\javaw.exe" set "JAVACMD=%~dp0runtime\bin\javaw.exe"

if "%JAVACMD%"=="" (
    where javaw >nul 2>&1
    if not errorlevel 1 set "JAVACMD=javaw"
)
if "%JAVACMD%"=="" (
    where java >nul 2>&1
    if not errorlevel 1 set "JAVACMD=java"
)

if "%JAVACMD%"=="" (
    echo [CANH BAO] Khong tim thay thu muc jre di kem hoac Java tren may tinh!
    echo Vui long giu nguyen thu muc jre cung cho voi file chay nay.
    pause
    exit /b 1
)

echo [OK] Dang khoi chay Pimcore Auto Tool bang Java Portable...
echo.

start "" "%JAVACMD%" -Dfile.encoding=UTF-8 -jar "%~dp0PimcoreAutoTool.jar"
exit /b 0
