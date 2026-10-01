#!/usr/bin/env bash
# Pousse les secrets de la fonction Edge notify-new-items (clé APNs) depuis 1Password vers Supabase.
# Prérequis : item 1Password giftmanager/apns avec key_id, private_key (contenu du .p8).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
op vault list >/dev/null 2>&1 || { echo "Non connecté à 1Password : lance 'op signin'." >&2; exit 1; }

REF=$(op read op://giftmanager/supabase/project_ref)
ENV_FILE=$(mktemp); chmod 600 "$ENV_FILE"; trap 'rm -f "$ENV_FILE"' EXIT
{
  echo "APNS_KEY_ID=$(op read op://giftmanager/apns/key_id)"
  echo "APNS_TEAM_ID=$(op read op://giftmanager/apple-developer/team_id)"
  echo "APNS_TOPIC=$(op read op://giftmanager/apple-developer/bundle_id)"
  printf 'APNS_PRIVATE_KEY="%s"\n' "$(op read op://giftmanager/apns/private_key)"
} > "$ENV_FILE"
supabase secrets set --project-ref "$REF" --env-file "$ENV_FILE"
echo "✓ Secrets APNs envoyés à Supabase"
