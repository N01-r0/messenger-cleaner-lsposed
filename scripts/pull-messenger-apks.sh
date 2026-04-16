#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/analysis/apk"
PACKAGE="${1:-com.facebook.orca}"

mkdir -p "$DEST"

adb get-state >/dev/null

while IFS= read -r remote_path; do
  [[ -n "$remote_path" ]] || continue
  file_name="$(basename "$remote_path")"
  adb pull "$remote_path" "$DEST/$file_name"
done < <(adb shell pm path "$PACKAGE" | sed 's/^package://')

aapt dump badging "$DEST/base.apk" | head -n 40 > "$ROOT/notes/current-apk-badging.txt"
