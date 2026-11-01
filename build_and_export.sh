#!/usr/bin/env bash
set -Eeuo pipefail

# Build the debug APK, then copy it to Download on one authorized Android
# device. Run from any directory with: bash build_and_export.sh
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
EXPORT_NAME="TRXZY_ModMenu.apk"
ADB=""

# Prefer an explicitly configured SDK, then local.properties, then common paths.
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" && -f "$ROOT_DIR/local.properties" ]]; then
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" | tail -n 1)"
fi
if [[ -z "$SDK_DIR" ]]; then
    for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/local/android-sdk"; do
        if [[ -d "$candidate" ]]; then
            SDK_DIR="$candidate"
            break
        fi
    done
fi

if [[ -n "$SDK_DIR" ]]; then
    export ANDROID_SDK_ROOT="$SDK_DIR"
    export ANDROID_HOME="$SDK_DIR"
    if [[ -x "$SDK_DIR/platform-tools/adb" ]]; then
        ADB="$SDK_DIR/platform-tools/adb"
    fi
    if [[ -d "$SDK_DIR/ndk" && -z "${ANDROID_NDK_HOME:-}" ]]; then
        NDK_DIR="$(find "$SDK_DIR/ndk" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -n 1)"
        if [[ -n "$NDK_DIR" ]]; then
            export ANDROID_NDK_HOME="$NDK_DIR"
        fi
    fi
fi

if [[ -z "$ADB" ]]; then
    ADB="$(command -v adb || true)"
fi

cd "$ROOT_DIR"
if [[ -x "$ROOT_DIR/gradlew" ]]; then
    echo "Building with the project Gradle wrapper..."
    "$ROOT_DIR/gradlew" --no-daemon assembleDebug
elif command -v gradle >/dev/null 2>&1; then
    echo "No Gradle wrapper is present; using the installed Gradle command."
    gradle --no-daemon assembleDebug
else
    echo "Error: neither a Gradle wrapper nor an installed Gradle command is available." >&2
    echo "Open the project in Android Studio or install Gradle 8.9, then rerun." >&2
    exit 1
fi

if [[ ! -s "$APK_PATH" ]]; then
    echo "Error: build completed but APK was not found at: $APK_PATH" >&2
    exit 1
fi

echo "Build succeeded: $APK_PATH"
if [[ -z "$ADB" ]]; then
    echo "Error: adb is unavailable, so the APK cannot be copied to a device." >&2
    exit 1
fi

# Avoid choosing a device implicitly if multiple devices are connected.
ADB_ARGS=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB_ARGS=(-s "$ANDROID_SERIAL")
else
    mapfile -t ONLINE_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
        echo "Error: expected exactly one authorized device; found ${#ONLINE_DEVICES[@]}." >&2
        echo "Connect/authorize one device or set ANDROID_SERIAL. APK remains at $APK_PATH" >&2
        exit 1
    fi
    ADB_ARGS=(-s "${ONLINE_DEVICES[0]}")
fi

DEST_DIR="/sdcard/Download"
"$ADB" "${ADB_ARGS[@]}" shell mkdir -p "$DEST_DIR"
"$ADB" "${ADB_ARGS[@]}" push "$APK_PATH" "$DEST_DIR/$EXPORT_NAME"
echo "Exported APK to device: $DEST_DIR/$EXPORT_NAME"
