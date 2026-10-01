#!/usr/bin/env bash
# Publie un build sur TestFlight.
# Apple exige le SDK iOS 26 (Xcode 26) : le build tourne sur GitHub Actions (workflow ios-release.yml),
# qui gère aussi les capacités du bundle ID et le profil App Store.
#
# Usage : scripts/apple/release.sh [branche]   (défaut : main)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REF="${1:-main}"

# Secrets CI à jour depuis 1Password (facultatif si 1Password est verrouillé : les secrets GitHub existent déjà).
"$ROOT/scripts/secrets/sync-github.sh" >/dev/null 2>&1 || echo "• 1Password indisponible : secrets GitHub inchangés"

gh workflow run ios-release.yml --ref "$REF"
sleep 5
RUN_ID=$(gh run list --workflow ios-release.yml --branch "$REF" --limit 1 --json databaseId -q '.[0].databaseId')
echo "▶ Build lancé : $(gh run view "$RUN_ID" --json url -q .url)"
gh run watch "$RUN_ID" --exit-status
