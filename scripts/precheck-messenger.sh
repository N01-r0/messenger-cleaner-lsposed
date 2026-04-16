#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="$ROOT/reports"

if [[ $# -eq 0 ]]; then
  exec "$ROOT/scripts/check-messenger-compat.py" --device --report-dir "$REPORT_DIR"
fi

if [[ "$1" == "--device" ]]; then
  exec "$ROOT/scripts/check-messenger-compat.py" --device --report-dir "$REPORT_DIR" "${@:2}"
fi

exec "$ROOT/scripts/check-messenger-compat.py" "$1" --report-dir "$REPORT_DIR" "${@:2}"
