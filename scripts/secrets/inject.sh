#!/usr/bin/env bash
# Génère les fichiers de configuration locaux à partir des modèles et de 1Password :
#   - ios/Config/Secrets.xcconfig
#   - android/app/secrets.properties
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TPL="$ROOT/ios/Config/Secrets.xcconfig.tpl"
OUT="$ROOT/ios/Config/Secrets.xcconfig"
ANDROID_TPL="$ROOT/android/secrets.properties.tpl"
ANDROID_OUT="$ROOT/android/app/secrets.properties"

op vault list >/dev/null 2>&1 || { echo "Non connecté à 1Password : lance 'op signin'." >&2; exit 1; }

op inject --force -i "$TPL" -o "$OUT" >/dev/null
chmod 600 "$OUT"

op inject --force -i "$ANDROID_TPL" -o "$ANDROID_OUT" >/dev/null
chmod 600 "$ANDROID_OUT"

for generated in "$OUT" "$ANDROID_OUT"; do
  if grep -q A_RENSEIGNER "$generated"; then
    echo "⚠️  Valeurs encore à renseigner dans 1Password (coffre giftmanager) — $generated :" >&2
    grep A_RENSEIGNER "$generated" | cut -d= -f1 | sed 's/^/   - /' >&2
  fi
done
echo "✓ $OUT généré"
echo "✓ $ANDROID_OUT généré"
