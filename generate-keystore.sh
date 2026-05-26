#!/usr/bin/env bash
# Generate a release keystore for signing the APK.
# Run this once, then fill in keystore.properties with the password you choose.

set -euo pipefail

KEYSTORE="release.keystore"
ALIAS="release"
VALIDITY=10000

if [ -f "$KEYSTORE" ]; then
    echo "Keystore $KEYSTORE already exists. Delete it first if you want to regenerate."
    exit 1
fi

echo "Generating keystore: $KEYSTORE"
echo "Key alias: $ALIAS"
echo "Validity: $VALIDITY days"
echo ""

keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY"

echo ""
echo "Keystore created. Now:"
echo "  1. Copy keystore.properties.example to keystore.properties"
echo "  2. Fill in the password you just set"
echo ""
echo "  cp keystore.properties.example keystore.properties"
echo "  # Edit keystore.properties with your editor"
