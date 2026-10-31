#!/usr/bin/env bash
# ==============================================================================
#                  PIMCORE AUTO TOOL - ENTERPRISE v2.0 (macOS)
# ==============================================================================

# Chuyển về thư mục chứa file script này
cd "$(dirname "$0")" || exit 1

echo "=============================================================================="
echo "                   PIMCORE AUTO TOOL - ENTERPRISE v2.0"
echo "        Tự động hoá Google Docs -> Pimcore CMS -> Web App Script (macOS)"
echo "=============================================================================="
echo ""

# 1. Kiểm tra Java
if ! command -v java &>/dev/null; then
    echo "[CẢNH BÁO] Máy tính Mac của bạn chưa cài đặt Java!"
    echo ""
    echo "Để sử dụng tool, bạn hãy tải và cài đặt Java 17 hoặc 21 (miễn phí) tại:"
    echo "  https://adoptium.net/temurin/releases/?version=17"
    echo "  (Chọn file cài đặt .pkg dành cho macOS x64 hoặc aarch64 Apple Silicon)"
    echo ""
    echo "Hoặc cài đặt qua Homebrew bằng lệnh trong Terminal:"
    echo "  brew install openjdk@17"
    echo ""
    read -n 1 -s -r -p "Bấm phím bất kỳ để thoát..."
    echo ""
    exit 1
fi

# 2. Khởi chạy Tool
echo "[OK] Đã phát hiện Java: $(java -version 2>&1 | head -n 1)"
echo "Đang khởi chạy giao diện Pimcore Auto Tool..."
echo ""

JAR_FILE="PimcoreAutoTool.jar"
if [ ! -f "$JAR_FILE" ]; then
    if [ -f "target/PimcoreAutoTool.jar" ]; then
        JAR_FILE="target/PimcoreAutoTool.jar"
    else
        echo "[LỖI] Không tìm thấy file PimcoreAutoTool.jar!"
        read -n 1 -s -r -p "Bấm phím bất kỳ để thoát..."
        echo ""
        exit 1
    fi
fi

# Chạy với UTF-8 và hỗ trợ Apple ScreenMenu
java -Dfile.encoding=UTF-8 \
     -Dapple.laf.useScreenMenuBar=true \
     -Dapple.awt.application.name="Pimcore Auto Tool" \
     -jar "$JAR_FILE" &

exit 0

