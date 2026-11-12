#!/usr/bin/env bash
# ADBUtility Guard - Script de Verificación y Configuración de Entorno (Linux / macOS)
set -e

echo -e "\033[1;36m==========================================================\033[0m"
echo -e "\033[1;36m ADBUtility Guard - Verificación y Configuración de Entorno\033[0m"
echo -e "\033[1;36m==========================================================\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Verificación de Java JDK
echo -e "\n\033[1;33m[*] 1. Comprobando Java JDK...\033[0m"
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo -e "\033[1;32m[✓] Java detectado: $JAVA_VER\033[0m"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo -e "\033[1;32m[✓] Java detectado desde JAVA_HOME: $JAVA_VER\033[0m"
else
    echo -e "\033[1;31m[!] ERROR: No se encontró Java JDK instalado o en el PATH.\033[0m"
    echo -e "\033[1;33m    Instala OpenJDK 17 o 21 (ej: sudo apt install openjdk-17-jdk) y define JAVA_HOME.\033[0m"
    exit 1
fi

# 2. Verificación de Android SDK
echo -e "\n\033[1;33m[*] 2. Comprobando Android SDK...\033[0m"
SDK_PATH=""

if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    SDK_PATH="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    SDK_PATH="$ANDROID_SDK_ROOT"
elif [ -d "$HOME/Android/Sdk" ]; then
    SDK_PATH="$HOME/Android/Sdk"
    export ANDROID_HOME="$SDK_PATH"
elif [ -d "$HOME/Library/Android/sdk" ]; then
    SDK_PATH="$HOME/Library/Android/sdk"
    export ANDROID_HOME="$SDK_PATH"
fi

if [ -z "$SDK_PATH" ]; then
    echo -e "\033[1;31m[!] No se encontró Android SDK configurado.\033[0m"
    echo -e "\033[1;33m    Descarga Android Command-line Tools desde:\033[0m"
    echo -e "    https://developer.android.com/studio#command-line-tools-only"
    echo -e "\033[1;33m    Luego define la variable ANDROID_HOME apuntando al directorio raíz del SDK.\033[0m"
    exit 1
fi

echo -e "\033[1;32m[✓] ANDROID_HOME detectado en: $SDK_PATH\033[0m"

# 3. Configuración de local.properties
echo -e "\n\033[1;33m[*] 3. Configurando local.properties...\033[0m"
echo "sdk.dir=$SDK_PATH" > ./local.properties
echo -e "\033[1;32m[✓] Archivo local.properties generado correctamente.\033[0m"

# 4. Aceptación explícita de licencias del SDK
echo -e "\n\033[1;33m[*] 4. Comprobando licencias de Android SDK...\033[0m"
mkdir -p "$SDK_PATH/licenses"
cat << 'EOF' > "$SDK_PATH/licenses/android-sdk-license"
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
24333f46a63b74f476e60b86a877987034b7f846
EOF
echo -e "\033[1;32m[✓] Licencias de Android SDK registradas y aceptadas.\033[0m"

# 5. Permisos de ejecución de gradlew
echo -e "\n\033[1;33m[*] 5. Verificando Gradle Wrapper...\033[0m"
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    echo -e "\033[1;32m[✓] Permiso de ejecución otorgado a ./gradlew\033[0m"
else
    echo -e "\033[1;31m[!] No se encontró ./gradlew\033[0m"
fi

echo -e "\n\033[1;32m==========================================================\033[0m"
echo -e "\033[1;32m [✓] Entorno listo para compilar sin Android Studio\033[0m"
echo -e "\033[1;32m     Ejecuta ./build-debug.sh para generar el APK Debug\033[0m"
echo -e "\033[1;32m==========================================================\033[0m"

