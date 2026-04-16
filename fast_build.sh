#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="$(pwd)"
APK_NAME="mycallenderjr.apk"

cd "$SCRIPT_DIR"

have_any_release_var=0
for name in RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
    if [[ -n "${!name:-}" ]]; then
        have_any_release_var=1
        break
    fi
done

if [[ "$have_any_release_var" -eq 1 ]]; then
    missing_vars=()
    for name in RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
        if [[ -z "${!name:-}" ]]; then
            missing_vars+=("$name")
        fi
    done

    if [[ "${#missing_vars[@]}" -gt 0 ]]; then
        printf 'Missing release signing variables: %s\n' "${missing_vars[*]}" >&2
        exit 1
    fi

    ./gradlew assembleRelease \
        -PRELEASE_STORE_FILE="$RELEASE_STORE_FILE" \
        -PRELEASE_STORE_PASSWORD="$RELEASE_STORE_PASSWORD" \
        -PRELEASE_KEY_ALIAS="$RELEASE_KEY_ALIAS" \
        -PRELEASE_KEY_PASSWORD="$RELEASE_KEY_PASSWORD"
    cp "app/build/outputs/apk/release/$APK_NAME" "$OUTPUT_DIR/$APK_NAME"
    printf 'Signed release APK created: %s/%s\n' "$OUTPUT_DIR" "$APK_NAME"
else
    ./gradlew assembleDebug
    cp "app/build/outputs/apk/debug/$APK_NAME" "$OUTPUT_DIR/$APK_NAME"
    printf 'Debug APK created: %s/%s\n' "$OUTPUT_DIR" "$APK_NAME"
fi
