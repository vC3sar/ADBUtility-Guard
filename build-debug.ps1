# ADBUtility Guard - Compilación de APK Debug (Windows PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " ADBUtility Guard - Build Debug APK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Ejecutar verificación de entorno si falta local.properties
if (-not (Test-Path ".\local.properties")) {
    Write-Host "[*] Archivo local.properties no encontrado. Ejecutando setup.ps1..." -ForegroundColor Yellow
    & ".\setup.ps1"
}

Write-Host "[*] Compilando APK Debug con Gradle Wrapper..." -ForegroundColor Yellow
$startTime = Get-Date

cmd.exe /c "gradlew.bat assembleDebug"
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[!] ERROR: Falló la compilación de Gradle (código $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

$expectedApk = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $expectedApk) {
    $apkItem = Get-Item $expectedApk
    $apkSizeMb = [math]::Round($apkItem.Length / 1MB, 2)
    $duration = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)

    Write-Host "`n========================================" -ForegroundColor Green
    Write-Host " [✓] COMPILACIÓN EXITOSA ($duration s)" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host " APK generado: $expectedApk" -ForegroundColor White
    Write-Host " Tamaño:       $apkSizeMb MB ($($apkItem.Length) bytes)" -ForegroundColor White
    Write-Host " Fecha:        $($apkItem.LastWriteTime)" -ForegroundColor White
} else {
    Write-Host "[!] ERROR: El comando terminó pero no se encontró el archivo esperado: $expectedApk" -ForegroundColor Red
    exit 1
}

