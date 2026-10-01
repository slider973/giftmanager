#!/usr/bin/env bash
# Copie les secrets nécessaires à la CI depuis 1Password vers les secrets GitHub Actions.
# Les valeurs transitent par stdin (jamais en argument de commande).
# Les secrets encore à A_RENSEIGNER sont ignorés.
set -euo pipefail

REPO="${REPO:-slider973/giftmanager}"

op vault list >/dev/null 2>&1 || { echo "Non connecté à 1Password : lance 'op signin'." >&2; exit 1; }

# NOM_DU_SECRET_GITHUB=référence op://
SECRETS=(
  "PROJECT_TOKEN=op://giftmanager/github/project_token"
  "SUPABASE_ACCESS_TOKEN=op://giftmanager/supabase/access_token"
  "SUPABASE_PROJECT_REF=op://giftmanager/supabase/project_ref"
  "SUPABASE_DB_PASSWORD=op://giftmanager/supabase-db/password"
  "APPLE_TEAM_ID=op://giftmanager/apple-developer/team_id"
  "ASC_KEY_ID=op://giftmanager/app-store-connect/key_id"
  "ASC_ISSUER_ID=op://giftmanager/app-store-connect/issuer_id"
  "ASC_PRIVATE_KEY=op://giftmanager/app-store-connect/private_key"
)

for entry in "${SECRETS[@]}"; do
  name=${entry%%=*}
  ref=${entry#*=}
  value=$(op read "$ref")
  if [ "$value" = "A_RENSEIGNER" ] || [ -z "$value" ]; then
    echo "• $name ignoré (à renseigner dans 1Password)"
    continue
  fi
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO"
  echo "✓ $name"
done
