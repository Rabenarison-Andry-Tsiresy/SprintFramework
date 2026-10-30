#!/bin/bash

# ==============================================================
# Script de build et déploiement du Sprint Framework
# Usage:
#   ./deploy.sh               -> compile et génère Framework.jar
#   ./deploy.sh <dest_dir>    -> compile, génère et copie le jar vers <dest_dir>
# ==============================================================

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== [1/3] Nettoyage... ===${NC}"
rm -rf build Framework.jar
mkdir -p build/classes

echo -e "${BLUE}=== [2/3] Compilation (avec conservation des noms de paramètres)... ===${NC}"
javac -parameters -cp "lib/*" -d build/classes $(find src -name "*.java")

echo -e "${BLUE}=== [3/3] Création du Framework.jar... ===${NC}"
jar cf Framework.jar -C build/classes .

echo -e "${GREEN}✓ Framework.jar généré avec succès !${NC}"

# Déploiement optionnel si un chemin cible est fourni
if [ -n "$1" ]; then
    DEST="$1"
    echo -e "${BLUE}=== Déploiement vers : $DEST ===${NC}"
    if [ -d "$DEST" ]; then
        cp Framework.jar "$DEST/"
        echo -e "${GREEN}✓ Framework.jar copié dans $DEST${NC}"
    else
        echo -e "${RED}Attention : Le dossier de destination \"$DEST\" n'existe pas.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}Terminé avec succès.${NC}"