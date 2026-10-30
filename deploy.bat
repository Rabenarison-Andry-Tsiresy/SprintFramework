@echo off
setlocal enabledelayedexpansion

echo ==============================================================
echo  Compilation et generation du Sprint Framework
echo ==============================================================

echo [1/3] Nettoyage...
if exist build rmdir /s /q build
if exist Framework.jar del /f /q Framework.jar
if exist sources.txt del /f /q sources.txt
mkdir build\classes

echo [2/3] Compilation (avec -parameters)...
for /r src %%f in (*.java) do (
    echo %%f>> sources.txt
)

javac -parameters -cp "lib/*" -d build\classes @sources.txt

if errorlevel 1 (
    echo [ERREUR] La compilation a echoue.
    if exist sources.txt del /f /q sources.txt
    exit /b 1
)

if exist sources.txt del /f /q sources.txt

echo [3/3] Creation du Framework.jar...
jar cf Framework.jar -C build\classes .

echo.
echo Framework.jar genere avec succes.

if not "%~1"=="" (
    echo Deploiement vers %~1...
    if exist "%~1" (
        copy /y Framework.jar "%~1"
        echo Framework.jar copie dans %~1
    ) else (
        echo [ATTENTION] Le dossier de destination "%~1" n'existe pas.
    )
)

echo Termine avec succes.