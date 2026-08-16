#!/usr/bin/env bash
# Generates a local release keystore plus the `keystore.properties` file the
# Gradle build reads (see app/build.gradle.kts). Both outputs are gitignored,
# so secrets never end up in the repository.
#
# Usage:  ./tools/generate_keystore.sh
#   (requires `keytool`, part of any JDK)
#
# After running it, `./gradlew assembleRelease` produces a signed APK.
# For a real Play Store release, keep the keystore file and passwords safe
# and consider feeding the credentials from CI secrets instead (e.g. write
# keystore.properties from GitHub Actions secrets in a deploy workflow).
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE_DIR="keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/release.keystore"
PROPS_FILE="keystore.properties"
KEY_ALIAS="snake-rush"

# 32 hex chars from /dev/urandom (no external tool needed).
random_pass() { od -An -N16 -tx1 /dev/urandom | tr -d ' \n'; }

STORE_PASS="$(random_pass)"
KEY_PASS="$STORE_PASS"

if [[ -f "$KEYSTORE_FILE" || -f "$PROPS_FILE" ]]; then
    echo "A keystore and/or keystore.properties already exist — refusing to overwrite." >&2
    echo "Delete them first if you really want to regenerate." >&2
    exit 1
fi

mkdir -p "$KEYSTORE_DIR"

keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=Snake Rush, OU=Mobile, O=Snake Rush, L=, ST=, C=US"

cat > "$PROPS_FILE" <<EOF
storeFile=$KEYSTORE_FILE
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS
EOF

chmod 600 "$KEYSTORE_FILE" "$PROPS_FILE"
echo
echo "Created:"
echo "  $KEYSTORE_FILE  (keep this file — it is your release identity)"
echo "  $PROPS_FILE     (read by app/build.gradle.kts)"
echo "Both are gitignored. Back them up somewhere safe; losing the keystore"
echo "means you can never update the app on the Play Store."
