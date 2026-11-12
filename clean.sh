#!/usr/bin/env bash
# ADBUtility Guard - Limpieza de compilaciones (Linux / macOS)
set -e

echo -e "\033[1;36m========================================\033[0m"
echo -e "\033[1;36m ADBUtility Guard - Clean Build\033[0m"
echo -e "\033[1;36m========================================\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    echo -e "\033[1;33m[*] Ejecutando gradlew clean...\033[0m"
    ./gradlew clean
    echo -e "\033[1;32m[✓] Limpieza completada con éxito.\033[0m"
else
    echo -e "\033[1;31m[!] No se encontró el wrapper ./gradlew en el directorio.\033[0m"
    exit 1
fi

