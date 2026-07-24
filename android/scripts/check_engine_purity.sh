#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/engine/src"

if grep -rnE '^import (android|androidx|org\.webrtc)\.' "$SRC" >/dev/null 2>&1; then
  echo "engine purity: framework import found under engine/src"
  grep -rnE '^import (android|androidx|org\.webrtc)\.' "$SRC" || true
  exit 1
fi

if grep -rnE 'org\.webrtc' "$SRC" >/dev/null 2>&1; then
  echo "engine purity: org.webrtc reference found under engine/src"
  grep -rnE 'org\.webrtc' "$SRC" || true
  exit 1
fi

echo "engine purity check passed"
