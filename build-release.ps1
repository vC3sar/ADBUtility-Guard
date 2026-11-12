# ADBUtility Guard - Compilación de APK Release (Windows PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " ADBUtility Guard - Build Release APK" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

if (-not (Test-Path ".\local.properties")) {
    Write-Host "[*] Archivo local.properties no encontrado. Ejecutando setup.ps1..." -ForegroundColor Yellow
    & ".\setup.ps1"
}

# Comprobar keystore
$keystoreProps = ".\keystore.properties"
if (Test-Path $keystoreProps) {
    Write-Host "[✓] Detectado archivo de configuración keystore.properties para firma Release." -ForegroundColor Green
} else {
    Write-Host "[i] No se detectó keystore.properties." -ForegroundColor Yellow
    Write-Host "    Para generar un almacén de claves propio:" -ForegroundColor Cyan
    Write-Host "    keytool -genkey -v -keystore release.keystore -alias guard -keyalg RSA -keysize 2048 -validity 10000" -ForegroundColor Gray
    Write-Host "    Y crear keystore.properties con: storeFile, storePassword, keyAlias, keyPassword" -ForegroundColor Gray
    Write-Host "    (Compilando con configuración de firma estándar)..." -ForegroundColor Yellow
}

Write-Host "`n[*] Compilando APK Release con Gradle Wrapper..." -ForegroundColor Yellow
$startTime = Get-Date

cmd.exe /c "gradlew.bat assembleRelease"
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[!] ERROR: Falló la compilación de Release (código $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

$releaseDir = "app\build\outputs\apk\release"
$releaseApks = Get-ChildItem -Path $releaseDir -Filter "*.apk" -ErrorAction SilentlyContinue

if ($releaseApks) {
    $apkItem = $releaseApks[0]
    $apkSizeMb = [math]::Round($apkItem.Length / 1MB, 2)
    $duration = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)

    Write-Host "`n==========================================" -ForegroundColor Green
    Write-Host " [✓] COMPILACIÓN RELEASE EXITOSA ($duration s)" -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host " APK generado: $($apkItem.FullName)" -ForegroundColor White
    Write-Host " Tamaño:       $apkSizeMb MB ($($apkItem.Length) bytes)" -ForegroundColor White
    Write-Host " Fecha:        $($apkItem.LastWriteTime)" -ForegroundColor White
} else {
    Write-Host "[!] ERROR: No se encontró ningún APK generado en $releaseDir" -ForegroundColor Red
    exit 1
}

