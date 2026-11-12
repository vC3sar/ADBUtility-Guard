#!/usr/bin/env bash
# ADBUtility Guard - Compilación de APK Debug (Linux / macOS)
set -e

echo -e "\033[1;36m========================================\033[0m"
echo -e "\033[1;36m ADBUtility Guard - Build Debug APK\033[0m"
echo -e "\033[1;36m========================================\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "./local.properties" ]; then
    echo -e "\033[1;33m[*] local.properties no encontrado. Ejecutando setup.sh...\033[0m"
    bash ./setup.sh
fi

chmod +x ./gradlew
echo -e "\033[1;33m[*] Compilando APK Debug con Gradle Wrapper...\033[0m"

./gradlew assembleDebug

EXPECTED_APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$EXPECTED_APK" ]; then
    FILE_SIZE=$(du -h "$EXPECTED_APK" | cut -f1)
    echo -e "\n\033[1;32m========================================\033[0m"
    echo -e "\033[1;32m [✓] COMPILACIÓN EXITOSA\033[0m"
    echo -e "\033[1;32m========================================\033[0m"
    echo -e " APK generado: $EXPECTED_APK"
    echo -e " Tamaño:       $FILE_SIZE"
else
    echo -e "\033[1;31m[!] ERROR: No se encontró el APK generado en $EXPECTED_APK\033[0m"
    exit 1
fi

