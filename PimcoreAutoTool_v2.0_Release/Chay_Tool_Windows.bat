@echo off
chcp 65001 >nul
title Khởi động Pimcore Auto Tool (Windows)
cls

echo ==============================================================================
echo                      PIMCORE AUTO TOOL - ENTERPRISE v2.0
echo       Tự động hoá Google Docs -> Pimcore CMS -> Web App Script
echo ==============================================================================
echo.

:: 1. Kiểm tra môi trường Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [CẢNH BÁO] Máy tính của bạn chưa cài đặt Java hoặc chưa thêm Java vào PATH!
    echo.
    echo Để chạy tool, vui lòng tải và cài đặt Java 17 hoặc 21 (miễn phí) tại:
    echo https://adoptium.net/temurin/releases/?version=17
    echo (Chọn file cài đặt định dạng .msi cho Windows x64)
    echo.
    echo Sau khi cài đặt xong, hãy mở lại file này!
    echo ==============================================================================
    pause
    exit /b 1
)

:: 2. Khởi chạy Tool
echo [OK] Đã phát hiện Java. Đang khởi chạy giao diện Pimcore Auto Tool...
echo.

if exist "PimcoreAutoTool.jar" (
    start "" javaw -Dfile.encoding=UTF-8 -jar "PimcoreAutoTool.jar"
) else if exist "target\PimcoreAutoTool.jar" (
    start "" javaw -Dfile.encoding=UTF-8 -jar "target\PimcoreAutoTool.jar"
) else (
    echo [LỖI] Không tìm thấy file PimcoreAutoTool.jar trong thư mục hiện tại!
    pause
    exit /b 1
)

exit /b 0

