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

create google-oauth --category "API Credential" \
  "web_client_id[text]=$P" "web_client_secret[concealed]=$P" \
  "android_client_id_debug[text]=$P" "android_client_id_release[text]=$P" \
  "sha1_debug[text]=$P" "sha1_release[text]=$P" \
  "notesPlain=Connexion Google (Android). Client Web : web_client_id = serverClientId de l'app et client_id du provider Supabase, web_client_secret = secret du provider (serveur uniquement, jamais dans l'app). Clients Android debug/release : package ch.jonathanlemaine.giftmanager + empreinte SHA-1 correspondante (procédure dans docs/SECRETS.md)."

create cron --category Password --generate-password='letters,digits,40' \
  "notesPlain=Secret partagé pg_cron -> fonctions Edge planifiées (CRON_SECRET et secret Vault cron_secret)."

create fcm --category "API Credential" \
  "service_account[concealed]=$P" "project_id[text]=$P" "api_key[concealed]=$P" "app_id[text]=$P" "sender_id[text]=$P" \
  "notesPlain=Firebase Cloud Messaging (notifications push Android). service_account = JSON complet du compte de service (rôle Firebase Cloud Messaging API Admin), poussé vers Supabase (FCM_SERVICE_ACCOUNT) par sync-supabase.sh. project_id, api_key, app_id, sender_id : valeurs publiques du projet Firebase, lues par l'app Android au build (pas de google-services.json)."

create github --category "API Credential" \
  "project_token[concealed]=$P" \
  "notesPlain=PAT classique scopes repo + project, utilisé par la GitHub Action project-sync."

# Le fichier keystore ne peut pas être créé ici (il n'existe pas encore) : l'attacher avec
#   op item edit android-upload --vault giftmanager "keystore[file]=/chemin/upload.jks"
create android-upload --category "API Credential" \
  "keystore_password[concealed]=$P" "key_alias[text]=$P" "key_password[concealed]=$P" "sha1[text]=$P" \
  "notesPlain=Clé d'UPLOAD Google Play (distincte de la clé de signature, gérée par Google avec Play App Signing). Fichier keystore (.jks) à attacher au champ keystore (docs/SECRETS.md). sha1 = empreinte de la clé d'upload. Perte de la clé : réinitialisation possible via le support Play."

create google-play --category "API Credential" \
  "service_account_json[concealed]=$P" "package_name[text]=ch.jonathanlemaine.giftmanager" \
  "notesPlain=Compte de service Google Cloud autorisé dans la Play Console (publication sur la piste de test interne). service_account_json = contenu du fichier JSON de clé du compte de service."
