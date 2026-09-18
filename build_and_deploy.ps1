# Plantshelf - PowerShell Build & Deploy Script
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================================" -ForegroundColor Green
Write-Host "        Plantshelf - Збірка та тестування" -ForegroundColor Green
Write-Host "========================================================"
Write-Host ""

# 1. Пошук JDK
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\Program Files\Android\Android Studio\jbr") {
        $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
        Write-Host "[OK] Знайдено JDK Android Studio: $env:JAVA_HOME" -ForegroundColor Cyan
    } else {
        $fallbackJdk = "$env:LOCALAPPDATA\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
        if (Test-Path $fallbackJdk) {
            $env:JAVA_HOME = $fallbackJdk
            Write-Host "[OK] Знайдено резервний JDK 17: $env:JAVA_HOME" -ForegroundColor Cyan
        } else {
            Write-Host "[!] JAVA_HOME не встановлено. Перевірте встановлення JDK або Android Studio." -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "[OK] Використовується JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan
}

# 2. Пошук ADB
$adb = "adb"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    $sdkAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) {
        $adb = $sdkAdb
        Write-Host "[OK] Знайдено ADB: $adb" -ForegroundColor Cyan
    }
}

Write-Host ""
Write-Host "Оберіть дію:"
Write-Host " [1] Зібрати APK та запустити на емуляторі/телефоні (Швидкий тест)"
Write-Host " [2] Тільки зібрати APK"
Write-Host " [3] Опублікувати релізний тег на GitHub (Запуск збірки в GitHub Actions)"
Write-Host " [4] Вихід"
Write-Host ""

$choice = Read-Host "Ваш вибір (1-4)"

if ($choice -eq "4") {
    exit
}

if ($choice -eq "3") {
    $tag = Read-Host "Введіть версію тегу (наприклад, v1.0.1)"
    if ([string]::IsNullOrWhiteSpace($tag)) {
        Write-Host "[!] Тег не вказано. Скасовано." -ForegroundColor Red
        exit
    }
    Write-Host "Створення тегу $tag..." -ForegroundColor Cyan
    git tag $tag
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] Не вдалося створити тег. Можливо, такий тег вже існує." -ForegroundColor Red
        exit
    }
    Write-Host "Відправка тегу на GitHub..." -ForegroundColor Cyan
    git push origin $tag
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "[УСПІХ] Тег $tag відправлено на GitHub!" -ForegroundColor Green
        Write-Host "GitHub Actions розпочав автоматичну збірку релізу:" -ForegroundColor Green
        Write-Host "https://github.com/Homka13/Plantshelf/actions" -ForegroundColor Yellow
    }
    exit
}

Write-Host ""
Write-Host "Збірка APK через Gradle..." -ForegroundColor Cyan
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ПОМИЛКА] Збірка не вдалася." -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "[УСПІХ] APK зібрано: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green

if ($choice -eq "1") {
    Write-Host "Перевірка пристроїв ADB..." -ForegroundColor Cyan
    $devices = & $adb devices
    if ($devices -match "device\s*$") {
        Write-Host "[OK] Знайдено пристрій/емулятор. Встановлення APK..." -ForegroundColor Cyan
        & $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Запуск Plantshelf..." -ForegroundColor Cyan
            & $adb shell am start -n com.plantshelf.app/.ui.MainActivity
            Write-Host "[ГОТОВО] Додаток оновлено та запущено!" -ForegroundColor Green
        }
    } else {
        Write-Host "[!] Активний пристрій або емулятор не знайдено." -ForegroundColor Yellow
    }
}
