#! /bin/bash
echo "nettoyage..."
rm -rf build
mkdir -p build/classes

echo "compilation..."
javac -cp "lib/*" -d build/classes $(find src -name "*.java")

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "creation du jar..."
jar cf Framework.jar -C build/classes .

echo "terminé."