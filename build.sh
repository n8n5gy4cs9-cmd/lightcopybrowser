#!/bin/sh
# Build debug, unsigned release, and a verified, installable signed release.
set -eu
cd "$(dirname "$0")"

fail() { echo "Build failed: $*" >&2; exit 1; }

SDK_DIR=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
    SDK_DIR=$(sed -n 's/^sdk\.dir=//p' local.properties | head -n 1)
fi
[ -n "$SDK_DIR" ] || fail "Set ANDROID_SDK_ROOT or sdk.dir in local.properties."
TOOLS="$SDK_DIR/build-tools/${LIGHTCOPY_BUILD_TOOLS_VERSION:-35.0.0}"
[ -x "$TOOLS/zipalign" ] || fail "Missing $TOOLS/zipalign."
[ -x "$TOOLS/apksigner" ] || fail "Missing $TOOLS/apksigner."

if [ -n "${LIGHTCOPY_KEYSTORE:-}" ]; then
    [ -f "$LIGHTCOPY_KEYSTORE" ] || fail "Custom keystore does not exist."
    [ -n "${LIGHTCOPY_STORE_PASSWORD:-}" ] || fail "Set LIGHTCOPY_STORE_PASSWORD for the custom keystore."
else
    # Keep this identity outside build/ so clean builds do not change the signer.
    umask 077
    mkdir -p .signing
    LIGHTCOPY_KEYSTORE="$PWD/.signing/lightcopy-release.p12"
    if [ -f "$LIGHTCOPY_KEYSTORE" ] && [ ! -s .signing/store-password ]; then
        fail "Release key exists but its password file is missing; restore .signing/store-password."
    fi
    if [ ! -s .signing/store-password ]; then
        command -v openssl >/dev/null 2>&1 || fail "OpenSSL is required to initialize the release key."
        openssl rand -hex 32 > .signing/store-password
    fi
    LIGHTCOPY_STORE_PASSWORD=$(cat .signing/store-password)
fi
LIGHTCOPY_KEY_ALIAS=${LIGHTCOPY_KEY_ALIAS:-lightcopy}
LIGHTCOPY_KEY_PASSWORD=${LIGHTCOPY_KEY_PASSWORD:-$LIGHTCOPY_STORE_PASSWORD}
export LIGHTCOPY_STORE_PASSWORD LIGHTCOPY_KEY_PASSWORD

if [ ! -f "$LIGHTCOPY_KEYSTORE" ]; then
    command -v keytool >/dev/null 2>&1 || fail "JDK keytool is required."
    keytool -genkeypair -keystore "$LIGHTCOPY_KEYSTORE" -storetype PKCS12 \
        -alias "$LIGHTCOPY_KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
        -storepass:env LIGHTCOPY_STORE_PASSWORD -keypass:env LIGHTCOPY_KEY_PASSWORD \
        -dname "CN=LightCopy Browser"
    echo "Created persistent release key in .signing/. Back up this directory securely."
fi

./gradlew :app:assembleDebug :app:assembleRelease

DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_DIR="app/build/outputs/apk/release"
UNSIGNED_APK="$RELEASE_DIR/app-release-unsigned.apk"
SIGNED_APK="$RELEASE_DIR/app-release-signed.apk"
[ -f "$DEBUG_APK" ] || fail "Debug APK not found."
[ -f "$UNSIGNED_APK" ] || fail "Unsigned release APK not found."

WORK_DIR=$(mktemp -d "$RELEASE_DIR/.signing.XXXXXX")
trap 'rm -rf "$WORK_DIR"' EXIT
trap 'exit 1' HUP INT TERM
"$TOOLS/zipalign" -P 16 -f 4 "$UNSIGNED_APK" "$WORK_DIR/aligned.apk"
"$TOOLS/apksigner" sign --ks "$LIGHTCOPY_KEYSTORE" --ks-key-alias "$LIGHTCOPY_KEY_ALIAS" \
    --ks-pass env:LIGHTCOPY_STORE_PASSWORD --key-pass env:LIGHTCOPY_KEY_PASSWORD \
    --v4-signing-enabled false --out "$WORK_DIR/signed.apk" "$WORK_DIR/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$WORK_DIR/signed.apk"
"$TOOLS/zipalign" -c -P 16 4 "$WORK_DIR/signed.apk"
"$TOOLS/apksigner" verify "$DEBUG_APK"
[ "$(wc -c < "$WORK_DIR/signed.apk" | tr -d ' ')" -lt 15728640 ] || fail "Signed release exceeds the 15 MiB size gate."
mv "$WORK_DIR/signed.apk" "$SIGNED_APK"

printf '\nDebug (installable): %s\nUnsigned release (not installable): %s\nSigned release (installable): %s\n' \
    "$DEBUG_APK" "$UNSIGNED_APK" "$SIGNED_APK"
