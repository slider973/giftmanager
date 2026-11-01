#!/usr/bin/env bash
# Teste scripts/android/version-code.sh : déterministe, croissant, sous la limite Play.
set -euo pipefail
S="$(cd "$(dirname "$0")" && pwd)/version-code.sh"
fail() { echo "✗ $1" >&2; exit 1; }

a=$("$S" 2026-01-01T00:01:00Z); [ "$a" = 1 ] || fail "2026-01-01T00:01Z devrait donner 1, obtenu $a"
b=$("$S" 2026-06-15T12:30:00Z); b2=$("$S" 2026-06-15T12:30:00Z)
[ "$b" = "$b2" ] || fail "non déterministe"
[ "$b" = 238350 ] || fail "2026-06-15T12:30Z devrait donner 238350, obtenu $b"
c=$("$S" 2026-06-15T12:31:00Z); [ "$c" -gt "$b" ] || fail "non croissant"
d=$("$S" 2126-01-01T00:00:00Z); [ "$d" -lt 2100000000 ] || fail "dépasse la limite Play"
now=$("$S"); { [ "$now" -gt 0 ] && [ "$now" -lt 2100000000 ]; } || fail "maintenant hors bornes"
if "$S" 2025-12-31T23:59:00Z >/dev/null 2>&1; then fail "instant antérieur accepté"; fi
if "$S" n-importe-quoi >/dev/null 2>&1; then fail "format invalide accepté"; fi
echo "✓ version-code : toutes les vérifications passent"
