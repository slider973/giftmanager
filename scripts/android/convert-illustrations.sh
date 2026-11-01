#!/usr/bin/env bash
# Convertit les illustrations iOS (PNG) en WebP lossless avec alpha pour Android.
# Idempotent : relancer produit les mêmes fichiers. Nécessite `cwebp` (brew install webp).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$ROOT/ios/GiftManager/Resources/Illustrations.xcassets"
DST="$ROOT/android/app/src/main/res/drawable-nodpi"

if ! command -v cwebp >/dev/null 2>&1; then
  echo "Erreur : cwebp introuvable. Installe-le avec « brew install webp » (ou apt install webp)." >&2
  exit 1
fi

mkdir -p "$DST"
count=0
for dir in "$SRC"/*.imageset; do
  [ -d "$dir" ] || continue
  name="$(basename "$dir" .imageset)"
  png="$dir/$name.png"
  if [ ! -f "$png" ]; then
    echo "Erreur : $png introuvable." >&2
    exit 1
  fi
  cwebp -quiet -lossless -z 9 -exact "$png" -o "$DST/$name.webp"
  echo "ok  $name.webp"
  count=$((count + 1))
done
echo "$count illustration(s) converties vers $DST"
