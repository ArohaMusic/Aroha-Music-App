#!/usr/bin/env bash
# Secrets arrive via environment; never place passwords in command arguments or logs.
set -euo pipefail
set +x
: "${AROHA_KEYSTORE_BASE64:?Missing keystore secret}"
: "${AROHA_KEYSTORE_PASSWORD:?Missing keystore password}"
: "${AROHA_KEY_ALIAS:?Missing key alias}"
export AROHA_KEY_PASSWORD="${AROHA_KEY_PASSWORD:-$AROHA_KEYSTORE_PASSWORD}"
input=${1:?Unsigned APK required}
output=${2:?Signed APK output required}
tools=${3:?Android build-tools directory required}
test -f "$input"
test ! -e "$output" || { echo 'Signed output already exists; refusing to overwrite it'; exit 1; }
private_dir=$(mktemp -d)
trap 'rm -rf "$private_dir"' EXIT
chmod 700 "$private_dir"
printf '%s' "$AROHA_KEYSTORE_BASE64" | base64 --decode > "$private_dir/release.keystore"
chmod 600 "$private_dir/release.keystore"
mkdir -p "$(dirname "$output")"
"$tools/zipalign" -f 4 "$input" "$private_dir/aligned.apk"
"$tools/apksigner" sign --ks "$private_dir/release.keystore" \
  --ks-key-alias "$AROHA_KEY_ALIAS" \
  --ks-pass env:AROHA_KEYSTORE_PASSWORD --key-pass env:AROHA_KEY_PASSWORD \
  --v4-signing-enabled false --out "$private_dir/signed.apk" "$private_dir/aligned.apk"
"$tools/apksigner" verify --verbose "$private_dir/signed.apk"
"$tools/zipalign" -c 4 "$private_dir/signed.apk"
cp "$private_dir/signed.apk" "$output"
echo 'Signed APK verified.'
