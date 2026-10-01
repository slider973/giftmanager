#!/usr/bin/env bash
# Prépare la signature de distribution App Store sans "cloud signing" :
#  1. certificat Apple Distribution (créé via l'API si absent de 1Password),
#     stocké en .p12 dans 1Password (item apple-distribution) et importé dans le trousseau ;
#  2. profil App Store pour le bundle ID, installé localement.
# Idempotent : réutilise le certificat de 1Password et le profil existant s'ils sont valides.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ASC=(op run --env-file="$ROOT/.env.op" -- uv run -q "$ROOT/scripts/apple/asc.py")
VAULT=giftmanager
BUNDLE=$(op read op://$VAULT/apple-developer/bundle_id)
PROFILE_NAME="Famille Cadeaux App Store"

TMP=$(mktemp -d); chmod 700 "$TMP"; trap 'rm -rf "$TMP"' EXIT

# --- 1. Certificat --------------------------------------------------------
if ! op item get apple-distribution --vault $VAULT >/dev/null 2>&1; then
  echo "▶ Création d'un certificat Apple Distribution"
  openssl genrsa -out "$TMP/dist.key" 2048 2>/dev/null
  openssl req -new -key "$TMP/dist.key" -subj "/CN=Famille Cadeaux Distribution/C=CH" -out "$TMP/dist.csr"
  CSR=$(sed '1d;$d' "$TMP/dist.csr" | tr -d '\n')
  "${ASC[@]}" POST /v1/certificates \
    "{\"data\":{\"type\":\"certificates\",\"attributes\":{\"certificateType\":\"DISTRIBUTION\",\"csrContent\":\"$CSR\"}}}" \
    > "$TMP/cert.json"
  CERT_ID=$(jq -r .data.id "$TMP/cert.json")
  jq -r .data.attributes.certificateContent "$TMP/cert.json" | base64 -d > "$TMP/dist.cer"
  openssl x509 -inform DER -in "$TMP/dist.cer" -out "$TMP/dist.pem"
  P12_PASS=$(openssl rand -hex 16)
  openssl pkcs12 -export -legacy -inkey "$TMP/dist.key" -in "$TMP/dist.pem" \
    -name "Apple Distribution" -out "$TMP/dist.p12" -passout "pass:$P12_PASS" 2>/dev/null \
    || openssl pkcs12 -export -inkey "$TMP/dist.key" -in "$TMP/dist.pem" \
         -name "Apple Distribution" -out "$TMP/dist.p12" -passout "pass:$P12_PASS"
  op item create --vault $VAULT --category "API Credential" --title apple-distribution \
    "certificate_id[text]=$CERT_ID" "p12_password[concealed]=$P12_PASS" \
    "notesPlain=Certificat Apple Distribution (signature TestFlight / App Store). Fichier joint : p12." \
    "p12[file]=$TMP/dist.p12" >/dev/null
  echo "✓ Certificat $CERT_ID stocké dans 1Password (apple-distribution)"
fi

CERT_ID=$(op read op://$VAULT/apple-distribution/certificate_id)
op read --out-file "$TMP/dist.p12" "op://$VAULT/apple-distribution/p12" >/dev/null
security import "$TMP/dist.p12" -k "$HOME/Library/Keychains/login.keychain-db" \
  -P "$(op read op://$VAULT/apple-distribution/p12_password)" \
  -T /usr/bin/codesign -T /usr/bin/security -T /usr/bin/productbuild >/dev/null 2>&1 || true
echo "✓ Certificat importé dans le trousseau"

# --- 2. Profil App Store --------------------------------------------------
BUNDLE_ID=$("${ASC[@]}" GET "/v1/bundleIds?filter[identifier]=$BUNDLE" | jq -r '.data[0].id')
PROFILE_JSON=$("${ASC[@]}" GET "/v1/profiles?filter[name]=$(jq -rn --arg n "$PROFILE_NAME" '$n|@uri')&filter[profileState]=ACTIVE&include=certificates")
HAS_CERT=$(jq --arg c "$CERT_ID" '[.included[]? | select(.id==$c)] | length' <<<"$PROFILE_JSON")
if [ "$(jq '.data | length' <<<"$PROFILE_JSON")" = 0 ] || [ "$HAS_CERT" = 0 ]; then
  for old in $(jq -r '.data[].id' <<<"$PROFILE_JSON"); do "${ASC[@]}" DELETE "/v1/profiles/$old" >/dev/null || true; done
  echo "▶ Création du profil App Store"
  PROFILE_JSON=$("${ASC[@]}" POST /v1/profiles "{\"data\":{\"type\":\"profiles\",
    \"attributes\":{\"name\":\"$PROFILE_NAME\",\"profileType\":\"IOS_APP_STORE\"},
    \"relationships\":{\"bundleId\":{\"data\":{\"type\":\"bundleIds\",\"id\":\"$BUNDLE_ID\"}},
    \"certificates\":{\"data\":[{\"type\":\"certificates\",\"id\":\"$CERT_ID\"}]}}}}" | jq '{data:[.data]}')
fi
UUID=$(jq -r '.data[0].attributes.uuid' <<<"$PROFILE_JSON")
DEST="$HOME/Library/MobileDevice/Provisioning Profiles"
mkdir -p "$DEST"
jq -r '.data[0].attributes.profileContent' <<<"$PROFILE_JSON" | base64 -d > "$DEST/$UUID.mobileprovision"
echo "✓ Profil « $PROFILE_NAME » installé ($UUID)"
echo "$UUID" > "$ROOT/.build/profile-uuid"
