# ADBUtility Guard - Instalación en Dispositivo vía ADB (Windows PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " ADBUtility Guard - ADB Device Installer" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Localizar ADB
$adbPath = "adb"
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    if ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe")) {
        $adbPath = "$env:ANDROID_HOME\platform-tools\adb.exe"
    } elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") {
        $adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    } else {
        Write-Host "[!] ERROR: No se encontró adb.exe en el sistema." -ForegroundColor Red
        exit 1
    }
}

Write-Host "[*] Usando ADB: $adbPath" -ForegroundColor Gray

# 2. Comprobar dispositivos conectados
$devices = & $adbPath devices | Select-String -Pattern "\sdevice$"
if (-not $devices) {
    Write-Host "[!] No se detectó ningún dispositivo Android conectado con Depuración USB activa." -ForegroundColor Red
    Write-Host "    Conecta tu teléfono mediante cable USB y autoriza la depuración en pantalla." -ForegroundColor Yellow
    exit 1
}

Write-Host "[✓] Dispositivo detectado:" -ForegroundColor Green
$devices | ForEach-Object { Write-Host "    $_" -ForegroundColor Cyan }

# 3. Comprobar APK
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "[*] APK Debug no encontrado. Compilando primero..." -ForegroundColor Yellow
    & ".\build-debug.ps1"
}

# 4. Instalar APK
Write-Host "`n[*] Instalando APK en el dispositivo..." -ForegroundColor Yellow
& $adbPath install -r -d -g $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Error durante la instalación de la APK." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[✓] APK instalada exitosamente." -ForegroundColor Green

# 5. Iniciar la aplicación
Write-Host "`n[*] Abriendo ADBUtility Guard..." -ForegroundColor Yellow
& $adbPath shell am start -n com.adbutility.guard/.MainActivity | Out-Null

# 6. Facilitar activación del servicio de accesibilidad por ADB (opcional)
Write-Host "`n[TIP] Puedes habilitar el servicio de accesibilidad directamente vía ADB con:" -ForegroundColor Cyan
Write-Host "adb shell settings put secure enabled_accessibility_services com.adbutility.guard/com.adbutility.guard.ResetAccessibilityService" -ForegroundColor Gray
Write-Host "adb shell settings put secure accessibility_enabled 1" -ForegroundColor Gray

