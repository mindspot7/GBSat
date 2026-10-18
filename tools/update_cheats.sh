#!/bin/sh
# Refreshes the bundled Sega Saturn cheat database from libretro-database.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
git clone -q --depth 1 --filter=blob:none --sparse https://github.com/libretro/libretro-database.git "$TMP/db"
git -C "$TMP/db" sparse-checkout set "cht/Sega - Saturn"
DEST="$ROOT/apps/android/app/src/main/assets/cheats"
rm -f "$DEST"/*.cht
cp "$TMP/db/cht/Sega - Saturn/"*.cht "$DEST/"
cp "$TMP/db/LICENSE" "$DEST/LICENSE-libretro-database.txt"
rm -rf "$TMP"
echo "Updated $(ls "$DEST"/*.cht | wc -l) cheat files"
