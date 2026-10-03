#!/usr/bin/env bash
# Crée (si absents) le coffre 1Password "giftmanager" et ses items.
# Idempotent : n'écrase jamais un item existant.
# Les valeurs inconnues sont initialisées à A_RENSEIGNER, à remplir dans 1Password.
set -euo pipefail

VAULT="${OP_VAULT:-giftmanager}"
P=A_RENSEIGNER

op vault list >/dev/null 2>&1 || { echo "Non connecté à 1Password : lance 'op signin'." >&2; exit 1; }

op vault get "$VAULT" >/dev/null 2>&1 \
  || op vault create "$VAULT" --description "Famille Cadeaux (slider973/giftmanager)" --icon gears >/dev/null

create() {
  local title=$1; shift
  if op item get "$title" --vault "$VAULT" >/dev/null 2>&1; then
    echo "• $title (existe déjà)"
  else
    op item create --vault "$VAULT" --title "$title" "$@" >/dev/null
    echo "✓ $title créé"
  fi
}

create supabase --category "API Credential" \
  "project_ref[text]=$P" "url[url]=https://$P.supabase.co" "publishable_key[text]=$P" \
  "secret_key[concealed]=$P" "access_token[concealed]=$P" \
  "notesPlain=Projet Supabase prod. secret_key (service_role) : serveur/CI uniquement, jamais dans l'app iOS. access_token : jeton personnel pour la CLI supabase."

create supabase-db --category Password --generate-password='letters,digits,32' \
  "notesPlain=Mot de passe Postgres du projet Supabase prod (à utiliser à la création du projet)."

create apple-developer --category "API Credential" \
  "team_id[text]=$P" "bundle_id[text]=$P" \
  "notesPlain=Compte Apple Developer. Sign in with Apple natif : seul le bundle_id est déclaré dans Supabase (Auth > Apple > Client IDs)."

create app-store-connect --category "API Credential" \
  "key_id[text]=$P" "issuer_id[text]=$P" "private_key[concealed]=$P" \
  "notesPlain=Clé API App Store Connect (rôle App Manager) pour l'upload TestFlight. private_key = contenu du fichier AuthKey_XXXX.p8."

create cron --category Password --generate-password='letters,digits,40' \
  "notesPlain=Secret partagé pg_cron -> fonctions Edge planifiées (CRON_SECRET et secret Vault cron_secret)."

create github --category "API Credential" \
  "project_token[concealed]=$P" \
  "notesPlain=PAT classique scopes repo + project, utilisé par la GitHub Action project-sync."
