#!/usr/bin/env bash
# Creates the release keystore (once) and stores it as GitHub Actions secrets for this repo.
# Keep ~/.config/locker-signing safe: without it, new releases can't update existing installs.
set -euo pipefail

DIR="${LOCKER_SIGNING_DIR:-$HOME/.config/locker-signing}"
JKS="$DIR/release.jks"
CREDS="$DIR/credentials.env"

mkdir -p "$DIR"
chmod 700 "$DIR"

if [[ -f "$JKS" && ! -s "$CREDS" ]]; then
  mv "$JKS" "$JKS.broken-$(date +%s)"
  rm -f "$CREDS"
  echo "Moved a keystore without saved credentials aside."
fi

if [[ ! -f "$JKS" ]]; then
  PW="$(openssl rand -hex 24)"
  keytool -genkeypair -keystore "$JKS" -storetype PKCS12 -alias locker \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -storepass "$PW" -keypass "$PW" -dname "CN=Locker"
  printf 'KEYSTORE_PASSWORD=%s\nKEY_ALIAS=locker\nKEY_PASSWORD=%s\n' "$PW" "$PW" > "$CREDS"
  chmod 600 "$JKS" "$CREDS"
  echo "Created $JKS"
fi

# shellcheck source=/dev/null
source "$CREDS"
cd "$(dirname "$0")/.."
base64 -w0 "$JKS" | gh secret set KEYSTORE_BASE64
printf '%s' "$KEYSTORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD
printf '%s' "$KEY_ALIAS" | gh secret set KEY_ALIAS
printf '%s' "$KEY_PASSWORD" | gh secret set KEY_PASSWORD
gh secret list
