#!/usr/bin/env bash
cd "$(dirname "$0")" || exit 1

if ! command -v java &>/dev/null; then
    echo "[LỖI] Máy tính chưa có Java. Vui lòng cài đặt OpenJDK 17 (sudo apt install openjdk-17-jre hoặc tương đương)."
    exit 1
fi

JAR_FILE="PimcoreAutoTool.jar"
[ ! -f "$JAR_FILE" ] && JAR_FILE="target/PimcoreAutoTool.jar"

java -Dfile.encoding=UTF-8 -jar "$JAR_FILE" &
exit 0

