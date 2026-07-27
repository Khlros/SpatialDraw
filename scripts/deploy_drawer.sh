#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_NAME="com.example.myspatial"
ACTIVITY_NAME="${PACKAGE_NAME}/.app.LaunchActivity"
PICO_CONNECT_APP="/Applications/PICO Connect TMP.app"

if [[ -d "$PICO_CONNECT_APP" ]]; then
  open -a "$PICO_CONNECT_APP" >/dev/null 2>&1 || true
fi

echo "[1/4] Waiting for adb device..."
adb wait-for-device

echo "[2/4] Building and installing Drawer..."
cd "$ROOT_DIR"
./gradlew installDebug --no-daemon

echo "[3/4] Launching Drawer..."
adb shell am start -n "$ACTIVITY_NAME"

echo "[4/4] Checking resumed activity..."
adb shell dumpsys activity activities | rg -n "ResumedActivity|${PACKAGE_NAME}" || true

echo
echo "Drawer deploy completed."
