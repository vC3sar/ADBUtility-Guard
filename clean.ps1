# ADBUtility Guard - Limpieza de compilaciones (Windows PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " ADBUtility Guard - Clean Build" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

if (Test-Path ".\gradlew.bat") {
    Write-Host "[*] Ejecutando gradlew clean..." -ForegroundColor Yellow
    .\gradlew.bat clean
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[✓] Limpieza completada con éxito." -ForegroundColor Green
    } else {
        Write-Host "[!] Error durante la limpieza." -ForegroundColor Red
        exit $LASTEXITCODE
    }
} else {
    Write-Host "[!] No se encontró gradlew.bat en el directorio actual." -ForegroundColor Red
    exit 1
}

