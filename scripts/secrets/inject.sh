#!/usr/bin/env bash
# Génère ios/Config/Secrets.xcconfig à partir du modèle et de 1Password.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TPL="$ROOT/ios/Config/Secrets.xcconfig.tpl"
OUT="$ROOT/ios/Config/Secrets.xcconfig"

op vault list >/dev/null 2>&1 || { echo "Non connecté à 1Password : lance 'op signin'." >&2; exit 1; }

op inject --force -i "$TPL" -o "$OUT" >/dev/null
chmod 600 "$OUT"

if grep -q A_RENSEIGNER "$OUT"; then
  echo "⚠️  Valeurs encore à renseigner dans 1Password (coffre giftmanager) :" >&2
  grep A_RENSEIGNER "$OUT" | cut -d= -f1 | sed 's/^/   - /' >&2
fi
echo "✓ $OUT généré"
