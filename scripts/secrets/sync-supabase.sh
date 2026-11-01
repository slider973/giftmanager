#!/usr/bin/env bash
# Pousse les secrets des fonctions Edge (clé APNs, CRON_SECRET, compte de service FCM) depuis 1Password vers Supabase.
# Prérequis : item 1Password giftmanager/apns avec key_id, private_key (contenu du .p8) et giftmanager/cron avec secret ;
# pour Android (optionnel), giftmanager/fcm avec service_account (JSON du compte de service Firebase).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
op vault list >/dev/null 2>&1 || { echo "Non connecté à 1Password : lance 'op signin'." >&2; exit 1; }

REF=$(op read op://giftmanager/supabase/project_ref)
ENV_FILE=$(mktemp); chmod 600 "$ENV_FILE"; trap 'rm -f "$ENV_FILE"' EXIT
{
  echo "APNS_KEY_ID=$(op read op://giftmanager/apns/key_id)"
  echo "APNS_TEAM_ID=$(op read op://giftmanager/apple-developer/team_id)"
  echo "APNS_TOPIC=$(op read op://giftmanager/apple-developer/bundle_id)"
  echo "CRON_SECRET=$(op read op://giftmanager/cron/password)"
  printf 'APNS_PRIVATE_KEY="%s"\n' "$(op read op://giftmanager/apns/private_key)"
} > "$ENV_FILE"

# Compte de service FCM (notifications Android) : JSON compacté sur une ligne, entre apostrophes pour que les
# « \n » de la clé privée restent littéraux (JSON valide) au lieu d'être convertis en vrais retours à la ligne.
# Jamais affiché. Ignoré tant que l'item n'est pas renseigné : les notifications iOS continuent de fonctionner.
SUMMARY="APNs, CRON_SECRET"
FCM_JSON=$(op read op://giftmanager/fcm/service_account 2>/dev/null || true)
if [ -n "$FCM_JSON" ] && [ "$FCM_JSON" != "A_RENSEIGNER" ]; then
  FCM_COMPACT=$(printf '%s' "$FCM_JSON" | jq -c .) || { echo "fcm/service_account n'est pas un JSON valide." >&2; exit 1; }
  case "$FCM_COMPACT" in
    *"'"*) echo "fcm/service_account contient une apostrophe : format non pris en charge." >&2; exit 1 ;;
  esac
  printf "FCM_SERVICE_ACCOUNT='%s'\n" "$FCM_COMPACT" >> "$ENV_FILE"
  SUMMARY="$SUMMARY, FCM"
fi
supabase secrets set --project-ref "$REF" --env-file "$ENV_FILE"
echo "✓ Secrets ($SUMMARY) envoyés à Supabase"
