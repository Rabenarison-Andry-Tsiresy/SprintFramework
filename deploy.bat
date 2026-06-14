@echo off

echo nettoyage...
if exist build rmdir /s /q build
mkdir build\classes

echo compilation...

for /r src %%f in (*.java) do (
    echo %%f>> sources.txt
)

javac -cp "lib/*" -d build\classes @sources.txt

if errorlevel 1 (
    echo Compilation failed.
    del sources.txt
    exit /b 1
)

del sources.txt

echo creation du jar...
jar cf Framework.jar -C build\classes .

echo termine.
pause