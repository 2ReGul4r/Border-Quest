@echo off
echo ============================================
echo  Border Quest - Build du mod Fabric 1.21.1
echo ============================================

REM Vérifie que Java 21+ est disponible
java -version 2>NUL
if %ERRORLEVEL% NEQ 0 (
    echo ERREUR: Java n'est pas installe ou pas dans le PATH.
    echo Installez Java 21 depuis https://adoptium.net/
    pause
    exit /b 1
)

REM Télécharge le Gradle wrapper si le jar est manquant
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Telechargement du Gradle wrapper...
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
    if %ERRORLEVEL% NEQ 0 (
        echo ERREUR: Impossible de telecharger gradle-wrapper.jar
        echo Telechargez-le manuellement depuis https://github.com/gradle/gradle
        pause
        exit /b 1
    )
)

echo Compilation en cours...
call gradlew.bat build

if %ERRORLEVEL% EQU 0 (
    echo.
    echo BUILD REUSSI !
    echo Le fichier .jar se trouve dans : build\libs\border-quest-1.0.0.jar
    echo.
    echo Pour installer le mod :
    echo   Copiez build\libs\border-quest-1.0.0.jar dans le dossier mods\ du serveur
    echo.
) else (
    echo.
    echo ECHEC DU BUILD. Verifiez les erreurs ci-dessus.
    echo.
)

pause
