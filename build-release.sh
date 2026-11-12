#!/usr/bin/env bash
# ADBUtility Guard - Compilación de APK Release (Linux / macOS)
set -e

echo -e "\033[1;36m==========================================\033[0m"
echo -e "\033[1;36m ADBUtility Guard - Build Release APK\033[0m"
echo -e "\033[1;36m==========================================\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "./local.properties" ]; then
    echo -e "\033[1;33m[*] local.properties no encontrado. Ejecutando setup.sh...\033[0m"
    bash ./setup.sh
fi

if [ -f "./keystore.properties" ]; then
    echo -e "\033[1;32m[✓] Detectado keystore.properties para firma de producción.\033[0m"
else
    echo -e "\033[1;33m[i] No se detectó keystore.properties.\033[0m"
    echo -e "    Para generar un keystore propio:"
    echo -e "    keytool -genkey -v -keystore release.keystore -alias guard -keyalg RSA -keysize 2048 -validity 10000"
fi

chmod +x ./gradlew
echo -e "\033[1;33m[*] Compilando APK Release con Gradle Wrapper...\033[0m"

./gradlew assembleRelease

RELEASE_DIR="app/build/outputs/apk/release"
RELEASE_APK=$(find "$RELEASE_DIR" -maxdepth 1 -name "*.apk" | head -n 1)

if [ -n "$RELEASE_APK" ] && [ -f "$RELEASE_APK" ]; then
    FILE_SIZE=$(du -h "$RELEASE_APK" | cut -f1)
    echo -e "\n\033[1;32m==========================================\033[0m"
    echo -e "\033[1;32m [✓] COMPILACIÓN RELEASE EXITOSA\033[0m"
    echo -e "\033[1;32m==========================================\033[0m"
    echo -e " APK generado: $RELEASE_APK"
    echo -e " Tamaño:       $FILE_SIZE"
else
    echo -e "\033[1;31m[!] ERROR: No se encontró el APK Release en $RELEASE_DIR\033[0m"
    exit 1
fi

