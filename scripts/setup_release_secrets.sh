#!/usr/bin/env bash
# Run locally once. Upload secrets directly to GitHub; never print their values.
set -euo pipefail
set +x
repo=${1:-ArohaMusic/Aroha-Music-App}
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v gh >/dev/null || { echo 'Install GitHub CLI (brew install gh), then run gh auth login'; exit 1; }
command -v keytool >/dev/null || { echo 'Select JDK 17 with JAVA_HOME and add its bin directory to PATH'; exit 1; }
gh auth status >/dev/null 2>&1 || { echo 'Run gh auth login first'; exit 1; }
read -r -p "Signing keystore path [$HOME/ArohaMusicSigning/aroha-release.p12]: " keystore
keystore=${keystore:-$HOME/ArohaMusicSigning/aroha-release.p12}
read -r -p 'Key alias [aroha]: ' AROHA_KEY_ALIAS
export AROHA_KEY_ALIAS=${AROHA_KEY_ALIAS:-aroha}
read -r -s -p 'Keystore password: ' AROHA_KEYSTORE_PASSWORD
printf '\n'
export AROHA_KEYSTORE_PASSWORD
if [[ ! -f "$keystore" ]]; then
  read -r -s -p 'Confirm new password: ' confirmation
  printf '\n'
  [[ "$confirmation" == "$AROHA_KEYSTORE_PASSWORD" ]] || { echo 'Passwords differ'; exit 1; }
  mkdir -p "$(dirname "$keystore")"
  (umask 077; keytool -genkeypair -keystore "$keystore" -storetype PKCS12 \
    -alias "$AROHA_KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Aroha Music' -storepass:env AROHA_KEYSTORE_PASSWORD \
    -keypass:env AROHA_KEYSTORE_PASSWORD)
  AROHA_KEY_PASSWORD=$AROHA_KEYSTORE_PASSWORD
else
  read -r -s -p 'Key password (Enter if same as keystore): ' AROHA_KEY_PASSWORD
  printf '\n'
  AROHA_KEY_PASSWORD=${AROHA_KEY_PASSWORD:-$AROHA_KEYSTORE_PASSWORD}
fi
export AROHA_KEY_PASSWORD
keytool -list -keystore "$keystore" -alias "$AROHA_KEY_ALIAS" \
  -storepass:env AROHA_KEYSTORE_PASSWORD >/dev/null
certificate_fingerprint=$(keytool -exportcert -keystore "$keystore" -alias "$AROHA_KEY_ALIAS" \
  -storepass:env AROHA_KEYSTORE_PASSWORD | shasum -a 256 | awk '{print $1}')
gh variable set RELEASE_CERT_SHA256 --body "$certificate_fingerprint" --repo "$repo"
# Existing public releases require the same signing key, never a replacement key.
base64 < "$keystore" | gh secret set AROHA_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$AROHA_KEYSTORE_PASSWORD" | gh secret set AROHA_KEYSTORE_PASSWORD --repo "$repo"
printf '%s' "$AROHA_KEY_ALIAS" | gh secret set AROHA_KEY_ALIAS --repo "$repo"
printf '%s' "$AROHA_KEY_PASSWORD" | gh secret set AROHA_KEY_PASSWORD --repo "$repo"
read -r -p 'Path to Firebase google-services.json (Enter to skip): ' firebase_config
if [[ -n "$firebase_config" ]]; then
  test -f "$firebase_config"
  python3 "$(dirname "$0")/validate_firebase.py" "$firebase_config"
  base64 < "$firebase_config" | gh secret set GOOGLE_SERVICES_JSON_BASE64 --repo "$repo"
  gh variable set RELEASE_FIREBASE --body true --repo "$repo"
fi
unset AROHA_KEYSTORE_PASSWORD AROHA_KEY_PASSWORD confirmation
echo 'Release secrets configured. Back up the keystore and password privately.'
echo 'Firebase is opt-in for tag builds: RELEASE_FIREBASE=true requires the matching GOOGLE_SERVICES_JSON_BASE64 secret.'
