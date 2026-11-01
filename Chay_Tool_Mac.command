#!/usr/bin/env bash
# ==============================================================================
#                  PIMCORE AUTO TOOL - ENTERPRISE v2.0 (macOS)
# ==============================================================================

# Chuyển về thư mục chứa file script
cd "$(dirname "$0")" || exit 1
TOOL_DIR="$(pwd)"

echo "=============================================================================="
echo "                   PIMCORE AUTO TOOL - ENTERPRISE v2.0"
echo "        Tu dong hoa: Google Docs -> Pimcore CMS -> Web App Script (macOS)"
echo "=============================================================================="
echo ""

JAVA_BIN=""

# 1. Tu dong cap quyen thuc thi cho JRE nhung di kem (neu giai nen tu file zip)
if [ -d "$TOOL_DIR/jre" ]; then
    chmod +x "$TOOL_DIR/jre/Contents/Home/bin/"* 2>/dev/null || true
    chmod +x "$TOOL_DIR/jre/bin/"* 2>/dev/null || true
fi

# 2. Kiem tra JRE nhung di kem trong thu muc jre
if [ -x "$TOOL_DIR/jre/Contents/Home/bin/java" ]; then
    JAVA_BIN="$TOOL_DIR/jre/Contents/Home/bin/java"
    echo "[OK] Su dung moi truong Java nhung san di kem (Portable JRE mac)."
elif [ -x "$TOOL_DIR/jre/bin/java" ]; then
    JAVA_BIN="$TOOL_DIR/jre/bin/java"
    echo "[OK] Su dung moi truong Java nhung san di kem (Portable JRE mac)."
elif java -version &>/dev/null; then
    JAVA_BIN="java"
    echo "[OK] Su dung Java co san tren macOS: $(java -version 2>&1 | head -n 1)"
else
    echo "[THONG BAO] May Mac cua ban chua co Java."
    echo "Tool dang tu dong tai nhanh moi truong Portable Java (chi mat ~5-10 giay lan dau)..."
    echo ""

    ARCH=$(uname -m)
    DOWNLOAD_URL=""
    if [ "$ARCH" = "arm64" ]; then
        DOWNLOAD_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jre/hotspot/normal/eclipse"
    else
        DOWNLOAD_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jre/hotspot/normal/eclipse"
    fi

    mkdir -p "$TOOL_DIR/jre_temp"
    echo "Dang tai goi Java 17 ($ARCH)..."
    curl -L "$DOWNLOAD_URL" -o "$TOOL_DIR/jre_temp/jre.tar.gz"

    if [ -f "$TOOL_DIR/jre_temp/jre.tar.gz" ]; then
        echo "Dang giai nen..."
        mkdir -p "$TOOL_DIR/jre"
        tar -xzf "$TOOL_DIR/jre_temp/jre.tar.gz" -C "$TOOL_DIR/jre" --strip-components 1
        rm -rf "$TOOL_DIR/jre_temp"
        if [ -x "$TOOL_DIR/jre/Contents/Home/bin/java" ]; then
            JAVA_BIN="$TOOL_DIR/jre/Contents/Home/bin/java"
            echo "[OK] Cai dat Portable Java hoan tat!"
        elif [ -x "$TOOL_DIR/jre/bin/java" ]; then
            JAVA_BIN="$TOOL_DIR/jre/bin/java"
            echo "[OK] Cai dat Portable Java hoan tat!"
        fi
    fi
fi

if [ -z "$JAVA_BIN" ]; then
    echo "[LOI] Khong the khoi tao Java tu dong. Vui long cai dat Java 17 qua brew: brew install openjdk@17"
    read -n 1 -s -r -p "Bam phim bat ky de thoat..."
    echo ""
    exit 1
fi

echo "Dang khoi chay giao dien Pimcore Auto Tool..."
echo ""

"$JAVA_BIN" -Dfile.encoding=UTF-8 \
            -Dapple.laf.useScreenMenuBar=true \
            -Dapple.awt.application.name="Pimcore Auto Tool" \
            -jar "$TOOL_DIR/PimcoreAutoTool.jar"

EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo "[THONG BAO] Tool dung voi ma: $EXIT_CODE"
    read -n 1 -s -r -p "Nhan phim bat ky de dong..."
fi
exit $EXIT_CODE
