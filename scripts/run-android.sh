#!/bin/bash
# MiniPay Android — boot emulator + install + launch in one step
# Usage: bash scripts/run-android.sh

set -e

ANDROID_SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
EMULATOR="$ANDROID_SDK/emulator/emulator.exe"
ADB="$ANDROID_SDK/platform-tools/adb.exe"
AVD="Pixel_6_API_35"
APPDIR="$(cd "$(dirname "$0")/.." && pwd)/android"

echo "=== Check emulator ==="
BOOTED=$("$ADB" devices 2>/dev/null | grep -c 'emulator.*device$' || true)
if [ "$BOOTED" = "0" ]; then
  echo "Starting emulator $AVD..."
  "$EMULATOR" -avd "$AVD" &
  sleep 3
  "$ADB" -s emulator-5554 wait-for-device
  echo "Waiting for boot..."
  "$ADB" -s emulator-5554 shell 'while [ -z "$(getprop sys.boot_completed 2>/dev/null)" ]; do sleep 1; done'
  echo "Emulator ready."
else
  echo "Emulator already running."
fi

echo "=== Install ==="
cd "$APPDIR"
./gradlew :app:installDebug

echo "=== Launch ==="
"$ADB" -s emulator-5554 shell am start -n com.minipay.mobile/com.minipay.mobile.MainActivity

echo "=== Done. App running on $AVD ==="
