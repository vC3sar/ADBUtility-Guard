#!/usr/bin/env bash
# ADBUtility Guard - Instalación en Dispositivo vía ADB (Linux / macOS)
set -e

echo -e "\033[1;36m==========================================\033[0m"
echo -e "\033[1;36m ADBUtility Guard - ADB Device Installer\033[0m"
echo -e "\033[1;36m==========================================\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Localizar ADB
ADB_CMD="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB_CMD="$ANDROID_HOME/platform-tools/adb"
    else
        echo -e "\033[1;31m[!] ERROR: No se encontró el binario adb en el sistema.\033[0m"
        exit 1
    fi
fi

# 2. Comprobar dispositivo
CONNECTED_DEV=$($ADB_CMD devices | grep -w "device" | grep -v "List" || true)
if [ -z "$CONNECTED_DEV" ]; then
    echo -e "\033[1;31m[!] No se detectó ningún dispositivo Android conectado con Depuración USB activa.\033[0m"
    exit 1
fi
echo -e "\033[1;32m[✓] Dispositivo detectado:\033[0m\n$CONNECTED_DEV"

# 3. Comprobar APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo -e "\033[1;33m[*] APK no encontrado. Compilando...\033[0m"
    bash ./build-debug.sh
fi

# 4. Instalar APK
echo -e "\n\033[1;33m[*] Instalando APK en el dispositivo...\033[0m"
$ADB_CMD install -r -d -g "$APK_PATH"
echo -e "\033[1;32m[✓] APK instalada exitosamente.\033[0m"

# 5. Iniciar la app
echo -e "\n\033[1;33m[*] Abriendo ADBUtility Guard...\033[0m"
$ADB_CMD shell am start -n com.adbutility.guard/.MainActivity >/dev/null 2>&1 || true

echo -e "\n\033[1;36m[TIP] Puedes habilitar el servicio de accesibilidad directamente vía ADB con:\033[0m"
echo -e "adb shell settings put secure enabled_accessibility_services com.adbutility.guard/com.adbutility.guard.ResetAccessibilityService"
echo -e "adb shell settings put secure accessibility_enabled 1"

