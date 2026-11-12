# ADBUtility Guard - Script de Verificación y Configuración de Entorno (Windows PowerShell)
# Permite compilar y desarrollar SIN Android Studio
$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " ADBUtility Guard - Verificación y Configuración de Entorno" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Verificación de Java JDK
Write-Host "`n[*] 1. Comprobando Java JDK..." -ForegroundColor Yellow
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    }
}

if ($javaCmd) {
    $javaVerOutput = java -version 2>&1 | Out-String
    Write-Host "[✓] Java detectado:" -ForegroundColor Green
    Write-Host $javaVerOutput.Trim() -ForegroundColor Gray
} else {
    Write-Host "[!] ERROR: No se encontró Java JDK instalado o en el PATH." -ForegroundColor Red
    Write-Host "    Instala OpenJDK 17 o 21 (Temurin, Microsoft o Azul) y define JAVA_HOME." -ForegroundColor Yellow
    exit 1
}

# 2. Verificación de Android SDK
Write-Host "`n[*] 2. Comprobando Android SDK..." -ForegroundColor Yellow
$sdkPath = $null

if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $sdkPath = $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $sdkPath = $env:ANDROID_SDK_ROOT
} else {
    $defaultWinSdk = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $defaultWinSdk) {
        $sdkPath = $defaultWinSdk
        [System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkPath, "User")
        $env:ANDROID_HOME = $sdkPath
        Write-Host "[i] Detectado SDK en ruta estándar: $sdkPath" -ForegroundColor Cyan
    }
}

if (-not $sdkPath) {
    Write-Host "[!] No se encontró Android SDK configurado." -ForegroundColor Red
    Write-Host "    Puedes descargar Command-line Tools oficiales de Android desde:" -ForegroundColor Yellow
    Write-Host "    https://developer.android.com/studio#command-line-tools-only" -ForegroundColor Gray
    Write-Host "    Luego define ANDROID_HOME apuntando al directorio raíz del SDK." -ForegroundColor Yellow
    exit 1
}

Write-Host "[✓] ANDROID_HOME configurado en: $sdkPath" -ForegroundColor Green

# 3. Comprobación y creación de local.properties
Write-Host "`n[*] 3. Configurando local.properties..." -ForegroundColor Yellow
$escapedSdkPath = $sdkPath.Replace("\", "\\").Replace(":", "\:")
$localPropContent = "sdk.dir=$escapedSdkPath`n"
Set-Content -Path ".\local.properties" -Value $localPropContent -Encoding ASCII
Write-Host "[✓] Archivo local.properties generado correctamente." -ForegroundColor Green

# 4. Comprobación de licencias de Android SDK
Write-Host "`n[*] 4. Comprobando licencias de Android SDK..." -ForegroundColor Yellow
$licenseDir = Join-Path $sdkPath "licenses"
if (-not (Test-Path $licenseDir)) {
    New-Item -ItemType Directory -Path $licenseDir -Force | Out-Null
}

# Hash oficial de licencias de Android SDK (android-sdk-license)
$androidSdkLicenseHash = "8933bad161af4178b1185d1a37fbf41ea5269c55`nd56f5187479451eabf01fb78af6dfcb131a6481e`n24333f46a63b74f476e60b86a877987034b7f846"
$licenseFile = Join-Path $licenseDir "android-sdk-license"
if (-not (Test-Path $licenseFile)) {
    Write-Host "[i] Registrando aceptación explícita de licencia de Android SDK..." -ForegroundColor Cyan
    Set-Content -Path $licenseFile -Value $androidSdkLicenseHash -Encoding ASCII
}
Write-Host "[✓] Licencias de Android SDK listas." -ForegroundColor Green

# 5. Comprobación de Gradle Wrapper
Write-Host "`n[*] 5. Verificando Gradle Wrapper..." -ForegroundColor Yellow
if (Test-Path ".\gradlew.bat") {
    Write-Host "[✓] gradlew.bat presente y listo." -ForegroundColor Green
} else {
    Write-Host "[!] Advertencia: No se encontró gradlew.bat." -ForegroundColor Red
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " [✓] Entorno listo para compilar sin Android Studio" -ForegroundColor Green
Write-Host "     Ejecuta .\build-debug.ps1 para generar el APK Debug" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

