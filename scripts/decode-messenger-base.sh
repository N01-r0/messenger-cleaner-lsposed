#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/analysis/apk/base.apk"
OUT="$ROOT/analysis/decompiled/base"
NOTES="$ROOT/notes/current-signatures.txt"

if [[ ! -f "$APK" ]]; then
  echo "Missing $APK. Run ./scripts/pull-messenger-apks.sh first." >&2
  exit 1
fi

apktool d -f "$APK" -o "$OUT"

{
  echo "Meta AI signatures:"
  rg -n "SearchAiagentImplementationsKillSwitch|AiAgentPluginsKillSwitch|Meta AI" "$OUT" | head -n 40 || true
  echo
  echo "Inbox ads signatures:"
  rg -n "InboxAdsItem|inboxads" "$OUT" | head -n 40 || true
} > "$NOTES"
