#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATUS=0

strip_strings() {
  sed -e 's/"[^"]*"//g' -e "s/'[^']*'//g"
}

check_kotlin() {
  local file="$1"
  local hits
  hits="$(strip_strings <"$file" | grep -nE '//|/\*|\*/' || true)"
  if [ -n "$hits" ]; then
    echo "no-comments: comment found in $file"
    echo "$hits"
    STATUS=1
  fi
}

check_xml() {
  local file="$1"
  local hits
  hits="$(grep -nE '<!--|-->' "$file" || true)"
  if [ -n "$hits" ]; then
    echo "no-comments: comment found in $file"
    echo "$hits"
    STATUS=1
  fi
}

while IFS= read -r -d '' file; do
  check_kotlin "$file"
done < <(
  for module in engine android rx2 ui-compose; do
    find "$ROOT/$module/src" -name '*.kt' -print0 2>/dev/null
    find "$ROOT/$module" -maxdepth 1 -name '*.gradle.kts' -print0 2>/dev/null
  done
  find "$ROOT" -maxdepth 1 -name '*.gradle.kts' -print0 2>/dev/null
)

while IFS= read -r -d '' file; do
  check_xml "$file"
done < <(
  for module in engine android rx2 ui-compose; do
    find "$ROOT/$module/src" -name '*.xml' -print0 2>/dev/null
  done
)

if [ "$STATUS" -ne 0 ]; then
  echo "no-comments check failed"
  exit 1
fi

echo "no-comments check passed"
