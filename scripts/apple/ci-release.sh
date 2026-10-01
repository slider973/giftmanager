#!/usr/bin/env bash
# Build + envoi TestFlight, exécuté par GitHub Actions (Xcode 26).
# Variables attendues (secrets GitHub, synchronisés depuis 1Password par scripts/secrets/sync-github.sh) :
#   SUPABASE_PROJECT_REF, SUPABASE_PUBLISHABLE_KEY, APPLE_TEAM_ID, APP_BUNDLE_ID,
#   ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY, DIST_P12_BASE64, DIST_P12_PASSWORD
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$ROOT/ios"
WORK="${RUNNER_TEMP:-$(mktemp -d)}/release"
BUILD_NUMBER="${BUILD_NUMBER:-$(date -u +%Y%m%d%H%M)}"
PROFILE_NAME="Famille Cadeaux App Store"
mkdir -p "$WORK"

# Config iOS (valeurs publiques côté client uniquement)
cat > "$IOS/Config/Secrets.xcconfig" <<EOF
SUPABASE_URL = https:/\$()/$SUPABASE_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY = $SUPABASE_PUBLISHABLE_KEY
DEVELOPMENT_TEAM = $APPLE_TEAM_ID
EOF

# Clé API App Store Connect (fichier temporaire du runner)
KEY="$WORK/AuthKey_$ASC_KEY_ID.p8"
printf '%s' "$ASC_PRIVATE_KEY" > "$KEY"; chmod 600 "$KEY"

# Trousseau temporaire avec le certificat de distribution
KC="$WORK/signing.keychain-db"; KC_PASS=$(openssl rand -hex 16)
security create-keychain -p "$KC_PASS" "$KC"
security set-keychain-settings -lut 3600 "$KC"
security unlock-keychain -p "$KC_PASS" "$KC"
printf '%s' "$DIST_P12_BASE64" | base64 --decode > "$WORK/dist.p12"
security import "$WORK/dist.p12" -k "$KC" -P "$DIST_P12_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security
security set-key-partition-list -S apple-tool:,apple: -s -k "$KC_PASS" "$KC" >/dev/null
security list-keychains -d user -s "$KC" $(security list-keychains -d user | tr -d '"')

# Profil App Store
PROFILES="$HOME/Library/MobileDevice/Provisioning Profiles"; mkdir -p "$PROFILES"
uv run -q "$ROOT/scripts/apple/asc.py" GET \
  "/v1/profiles?filter[name]=$(jq -rn --arg n "$PROFILE_NAME" '$n|@uri')&filter[profileState]=ACTIVE" > "$WORK/profile.json"
UUID=$(jq -r '.data[0].attributes.uuid' "$WORK/profile.json")
[ "$UUID" != null ] || { echo "Profil « $PROFILE_NAME » introuvable : lancer scripts/apple/setup-distribution.sh" >&2; exit 1; }
jq -r '.data[0].attributes.profileContent' "$WORK/profile.json" | base64 --decode > "$PROFILES/$UUID.mobileprovision"

(cd "$IOS" && xcodegen generate --quiet)

echo "▶ Archive (build $BUILD_NUMBER)"
xcodebuild archive \
  -project "$IOS/GiftManager.xcodeproj" -scheme GiftManager -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$WORK/GiftManager.xcarchive" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  OTHER_CODE_SIGN_FLAGS="--keychain $KC" -quiet

cat > "$WORK/ExportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>upload</string>
  <key>teamID</key><string>$APPLE_TEAM_ID</string>
  <key>signingStyle</key><string>manual</string>
  <key>signingCertificate</key><string>Apple Distribution</string>
  <key>provisioningProfiles</key>
  <dict><key>$APP_BUNDLE_ID</key><string>$UUID</string></dict>
  <key>uploadSymbols</key><true/>
  <key>manageAppVersionAndBuildNumber</key><false/>
</dict>
</plist>
EOF

echo "▶ Envoi vers App Store Connect"
xcodebuild -exportArchive \
  -archivePath "$WORK/GiftManager.xcarchive" -exportPath "$WORK/export" \
  -exportOptionsPlist "$WORK/ExportOptions.plist" \
  -authenticationKeyPath "$KEY" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID"

security delete-keychain "$KC" || true
echo "✓ Build $BUILD_NUMBER envoyé sur TestFlight"
echo "build_number=$BUILD_NUMBER" >> "${GITHUB_OUTPUT:-/dev/null}"
