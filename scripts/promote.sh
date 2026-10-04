#!/usr/bin/env bash
# Promeut une version validée vers la branche `production`.
#
# Usage :
#   scripts/promote.sh 1.1.0 --dry-run   # ce qui serait promu, sans rien modifier
#   scripts/promote.sh 1.1.0             # avance production sur le tag v1.1.0
#
# GitLab Flow : `main` est la ligne de développement, `production` suit ce qui tourne
# réellement chez les utilisateurs. On ne promeut qu'une version déjà publiée sur
# TestFlight **et** validée à l'usage — c'est elle qui sert de point de retour si une
# version suivante casse quelque chose.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-}"
DRY_RUN=false
[ "${2:-}" = "--dry-run" ] && DRY_RUN=true

die() { echo "✗ $1" >&2; exit 1; }

[ -n "$VERSION" ] || die "Usage : scripts/promote.sh <version> [--dry-run]   (ex. 1.1.0)"
printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || die "Version invalide : « $VERSION » (attendu X.Y.Z, sans préfixe v)"

TAG="v$VERSION"

[ -z "$(git status --porcelain)" ] || die "Modifications locales non commitées."
git fetch --quiet origin main production --tags

git rev-parse "$TAG" >/dev/null 2>&1 || die "Le tag $TAG n'existe pas : publie-le d'abord avec scripts/release.sh."

TARGET="$(git rev-parse "$TAG^{commit}")"
CURRENT="$(git rev-parse origin/production 2>/dev/null || echo '')"

if [ "$CURRENT" = "$TARGET" ]; then
  echo "— production pointe déjà sur $TAG, rien à faire."
  exit 0
fi

# production ne doit jamais reculer : sinon on remettrait en service du code déjà remplacé.
if [ -n "$CURRENT" ] && ! git merge-base --is-ancestor "$CURRENT" "$TARGET"; then
  die "$TAG n'est pas un descendant de production : promotion refusée (elle ferait reculer la production)."
fi

echo "▶ production : $(git describe --tags --always "$CURRENT" 2>/dev/null || echo 'vide') → $TAG"
if [ -n "$CURRENT" ]; then
  echo "▶ Changements promus :"
  git log --no-merges --pretty='- %s' "$CURRENT..$TARGET" | sed 's/^/    /'
fi
echo

if $DRY_RUN; then
  echo "— Essai à blanc : production inchangée."
  exit 0
fi

git push --quiet origin "$TARGET:refs/heads/production"
echo "✓ production pointe maintenant sur $TAG."
echo "  En cas de problème sur une version suivante, ce commit est le point de retour."
