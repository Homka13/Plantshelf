@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================================
echo        Plantshelf - Збірка та тестування
echo ========================================================
echo.

:: 1. Пошук Java
if not defined JAVA_HOME (
    if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
        echo [OK] Знайдено JDK Android Studio: !JAVA_HOME!
    ) else if exist "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma" (
        set "JAVA_HOME=%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
        echo [OK] Знайдено резервний JDK 17: !JAVA_HOME!
    ) else (
        echo [!] JAVA_HOME не встановлено. Перевірте встановлення JDK 17 або Android Studio.
    )
) else (
    echo [OK] Використовується JAVA_HOME: !JAVA_HOME!
)

:: 2. Пошук ADB
set "ADB_CMD=adb"
where adb >nul 2>nul
if %errorlevel% neq 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_CMD=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
        echo [OK] Знайдено ADB: !ADB_CMD!
    )
)

echo.
echo Оберіть дію:
echo  [1] Зібрати APK та запустити на емуляторі/телефоні (Швидкий тест)
echo  [2] Тільки зібрати APK
echo  [3] Опублікувати релізний тег на GitHub (Запуск збірки в GitHub Actions)
echo  [4] Вихід
echo.
set /p CHOICE="Ваш вибір (1-4): "

if "%CHOICE%"=="4" goto :eof

if "%CHOICE%"=="3" (
    echo.
    echo ========================================================
    echo      Публікація тегу на GitHub для створення Релізу
    echo ========================================================
    set /p TAG="Введіть версію тегу (наприклад, v1.0.1): "
    if "!TAG!"=="" (
        echo [!] Тег не вказано. Скасовано.
        pause
        goto :eof
    )
    echo Створення тегу !TAG!...
    git tag !TAG!
    if %errorlevel% neq 0 (
        echo [!] Не вдалося створити тег. Можливо, такий тег вже існує.
        pause
        goto :eof
    )
    echo Відправка тегу на GitHub...
    git push origin !TAG!
    if %errorlevel% equ 0 (
        echo.
        echo [УСПІХ] Тег !TAG! відправлено на GitHub!
        echo GitHub Actions розпочав автоматичну збірку релізу:
        echo https://github.com/Homka13/Plantshelf/actions
    ) else (
        echo [!] Помилка відправки тегу на GitHub.
    )
    pause
    goto :eof
)

echo.
echo ========================================================
echo          Збірка APK через Gradle...
echo ========================================================
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo.
    echo [ПОМИЛКА] Збірка не вдалася. Перевірте помилки вище.
    pause
    goto :eof
)

echo.
echo [УСПІХ] APK успішно зібрано:
echo app\build\outputs\apk\debug\app-debug.apk

if "%CHOICE%"=="1" (
    echo.
    echo Перевірка підключених пристроїв...
    "!ADB_CMD!" devices | findstr /R "device$" >nul
    if %errorlevel% equ 0 (
        echo [OK] Знайдено активний пристрій або емулятор.
        echo Встановлення APK...
        "!ADB_CMD!" install -r app\build\outputs\apk\debug\app-debug.apk
        if %errorlevel% equ 0 (
            echo Запуск додатку Plantshelf...
            "!ADB_CMD!" shell am start -n com.plantshelf.app/.ui.MainActivity
            echo.
            echo [ГОТОВО] Додаток оновлено та запущено на пристрої!
        ) else (
            echo [!] Помилка встановлення через ADB.
        )
    ) else (
        echo [!] Активний емулятор або телефон не знайдено.
        echo Запустіть емулятор в Android Studio або підключіть телефон по USB і повторіть.
    )
)

echo.
pause
