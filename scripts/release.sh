#!/usr/bin/env bash
# Publie une version : vérifie l'état du dépôt, pose un tag annoté, le pousse.
# Le tag déclenche .github/workflows/ios-release.yml (build signé + TestFlight).
#
# Usage :
#   scripts/release.sh 1.1.0           # pose et pousse le tag v1.1.0
#   scripts/release.sh 1.1.0 --dry-run # montre ce qui serait fait, sans rien modifier
#
# Convention (GitLab Flow) : on ne publie que depuis `main`, à jour et sans modification
# locale. La version du tag devient la version marketing de l'app ; le numéro de build
# reste un horodatage, toujours croissant comme l'exige App Store Connect.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-}"
DRY_RUN=false
[ "${2:-}" = "--dry-run" ] && DRY_RUN=true

die() { echo "✗ $1" >&2; exit 1; }

[ -n "$VERSION" ] || die "Usage : scripts/release.sh <version> [--dry-run]   (ex. 1.1.0)"
printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || die "Version invalide : « $VERSION » (attendu X.Y.Z, sans préfixe v)"

TAG="v$VERSION"

# — Garde-fous ———————————————————————————————————————————————
# Chacun évite une release qu'on ne pourrait plus reproduire ensuite.

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" = "main" ] || die "Release depuis « $BRANCH » : bascule sur main d'abord."

[ -z "$(git status --porcelain)" ] \
  || die "Modifications locales non commitées : le tag ne correspondrait pas au code publié."

git fetch --quiet origin main --tags
LOCAL="$(git rev-parse @)"
REMOTE="$(git rev-parse @{u})"
[ "$LOCAL" = "$REMOTE" ] \
  || die "main local et distant divergent : fais « git pull » (ou « git push ») avant de publier."

git rev-parse "$TAG" >/dev/null 2>&1 && die "Le tag $TAG existe déjà. Choisis une version supérieure."

# Une version doit être supérieure à la précédente, sinon TestFlight affiche un historique incohérent.
PREVIOUS="$(git tag --list 'v*' --sort=-v:refname | head -1)"
if [ -n "$PREVIOUS" ]; then
  HIGHEST="$(printf '%s\n%s\n' "${PREVIOUS#v}" "$VERSION" | sort -V | tail -1)"
  [ "$HIGHEST" = "$VERSION" ] || die "La version $VERSION est antérieure à $PREVIOUS."
fi

# — Notes de version ——————————————————————————————————————————
# Les commits depuis le dernier tag ; c'est ce que liront tes testeurs.
RANGE="${PREVIOUS:+$PREVIOUS..}HEAD"
NOTES="$(git log --no-merges --pretty='- %s' "$RANGE")"
[ -n "$NOTES" ] || die "Aucun commit depuis ${PREVIOUS:-le début} : rien à publier."

echo "▶ Version    : $VERSION  (précédente : ${PREVIOUS:-aucune})"
echo "▶ Commit     : $(git rev-parse --short HEAD)"
echo "▶ Changements :"
echo "$NOTES" | sed 's/^/    /'
echo

if $DRY_RUN; then
  echo "— Essai à blanc : aucun tag posé, aucun build lancé."
  exit 0
fi

git tag -a "$TAG" -m "$VERSION" -m "$NOTES"
git push --quiet origin "$TAG"

echo "✓ Tag $TAG poussé — le build TestFlight démarre."
echo "  Suivi : gh run list --workflow=ios-release.yml --limit 1"
