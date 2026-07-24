#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATTERN='driver|passenger|trip|rider'
STATUS=0

for module in engine android rx2; do
  MAIN="$ROOT/$module/src/main"
  if [ -d "$MAIN" ]; then
    if grep -rniE "$PATTERN" "$MAIN" >/dev/null 2>&1; then
      echo "purity: domain vocabulary found in $module/src/main"
      grep -rniE "$PATTERN" "$MAIN" || true
      STATUS=1
    fi
  fi
done

if [ "$STATUS" -ne 0 ]; then
  echo "purity check failed: remove driver/passenger/trip/rider from module sources"
  exit 1
fi

echo "purity check passed"
