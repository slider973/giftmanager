#!/usr/bin/env bash
# Publie un build sur TestFlight.
# Apple exige le SDK iOS 26 (Xcode 26) : le build tourne sur GitHub Actions (workflow ios-release.yml).
#
# Usage : scripts/apple/release.sh [branche]   (défaut : main)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REF="${1:-main}"

# Certificat + profil de distribution à jour, secrets CI synchronisés depuis 1Password.
"$ROOT/scripts/apple/setup-distribution.sh"
"$ROOT/scripts/secrets/sync-github.sh" >/dev/null

gh workflow run ios-release.yml --ref "$REF"
sleep 5
RUN_ID=$(gh run list --workflow ios-release.yml --branch "$REF" --limit 1 --json databaseId -q '.[0].databaseId')
echo "▶ Build lancé : $(gh run view "$RUN_ID" --json url -q .url)"
gh run watch "$RUN_ID" --exit-status
