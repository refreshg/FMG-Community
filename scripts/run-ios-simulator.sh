#!/bin/bash
# FMG Community — open iOS Simulator and run the app (iPhone 15 Pro)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UDID="D1BF7C6C-04B4-436A-BCB1-1375F98C1C07"
APP_PATH="$ROOT/ios/DerivedData/Build/Products/Debug-iphonesimulator/App.app"
BUNDLE_ID="ge.fmg.community"

export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

echo "→ Opening Simulator UI..."
defaults write com.apple.iphonesimulator CurrentDeviceUDID "$UDID" 2>/dev/null || true
open -a Simulator

echo "→ Booting iPhone 15 Pro (may take 1–2 min)..."
if ! xcrun simctl boot "$UDID" 2>/dev/null; then
  echo "   (device may already be booted)"
fi

if [[ ! -d "$APP_PATH" ]]; then
  echo "→ Building app..."
  cd "$ROOT"
  npx cap sync ios
  cd "$ROOT/ios/App"
  xcodebuild -workspace App.xcworkspace -scheme App \
    -destination "platform=iOS Simulator,id=$UDID" \
    -derivedDataPath "$ROOT/ios/DerivedData" \
    build
fi

echo "→ Installing & launching FMG Community..."
xcrun simctl install "$UDID" "$APP_PATH"
xcrun simctl launch "$UDID" "$BUNDLE_ID"

osascript -e 'tell application "Simulator" to activate' 2>/dev/null || true
echo "✓ Done. Look for the Simulator window (iPhone 15 Pro)."
