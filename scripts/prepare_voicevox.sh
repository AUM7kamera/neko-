#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/prepare_voicevox.sh <abi-binary-path> <device-path>
# Example: ./scripts/prepare_voicevox.sh ./voicevox_x86_64 /data/local/tmp/voicevox_x86_64

SRC="$1"
DST="$2"

if [ ! -f "$SRC" ]; then
  echo "Source binary not found: $SRC"
  exit 2
fi

echo "Pushing $SRC -> $DST"
adb push "$SRC" "$DST"
adb shell "chmod 755 $DST"
echo "Attempting to start in background..."
adb shell "$DST &"
echo "Done. Check logs with: adb logcat | grep -i voicevox"
