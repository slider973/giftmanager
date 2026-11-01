#!/usr/bin/env bash
# Calcule le versionCode Android : minutes écoulées depuis 2026-01-01T00:00Z.
# Google Play plafonne versionCode à 2 100 000 000 ; le format iOS YYYYMMDDHHMM le dépasse.
# Au rythme d'une minute, le compteur reste valide ~4 000 ans et croît toujours.
#
# Usage :
#   scripts/android/version-code.sh                        # maintenant
#   scripts/android/version-code.sh 2026-06-15T12:30:00Z   # instant donné (UTC, testable)
set -euo pipefail

# Compatible GNU date (CI Linux) et BSD date (macOS).
to_epoch() {
  date -u -d "$1" +%s 2>/dev/null || date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "$1" +%s
}

EPOCH=$(to_epoch 2026-01-01T00:00:00Z)

if [ -n "${1:-}" ]; then
  [[ "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
    || { echo "Instant invalide : « $1 » (attendu 2026-06-15T12:30:00Z)" >&2; exit 1; }
  NOW=$(to_epoch "$1")
else
  NOW=$(date -u +%s)
fi

CODE=$(( (NOW - EPOCH) / 60 ))
[ "$CODE" -ge 1 ] || { echo "Instant antérieur au 2026-01-01T00:01Z." >&2; exit 1; }
[ "$CODE" -le 2100000000 ] || { echo "versionCode au-dessus de la limite Play." >&2; exit 1; }
echo "$CODE"
