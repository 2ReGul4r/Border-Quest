@echo off
echo ============================================
echo  Border Quest - Installation sur le serveur
echo ============================================

set MOD_JAR=build\libs\border-quest-1.0.0.jar
set MODS_DIR=..\mods

if not exist "%MOD_JAR%" (
    echo ERREUR: Le jar n'existe pas encore. Lancez d'abord build.bat
    pause
    exit /b 1
)

if not exist "%MODS_DIR%" (
    echo Creation du dossier mods...
    mkdir "%MODS_DIR%"
)

echo Copie du mod vers %MODS_DIR%...
copy /Y "%MOD_JAR%" "%MODS_DIR%\border-quest-1.0.0.jar"

echo.
echo Installation terminee !
echo Redemarrez le serveur pour activer le mod.
echo.
pause
