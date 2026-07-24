#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:?usage: check_so_alignment.sh <dir-or-so>}"
ALIGN_REQUIRED=16384

find_readelf() {
  if [ -n "${READELF:-}" ]; then
    echo "$READELF"
    return
  fi
  local base="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  find "$base/ndk" -name 'llvm-readelf' 2>/dev/null | head -1
}

READELF_BIN="$(find_readelf)"
if [ -z "$READELF_BIN" ]; then
  echo "alignment: llvm-readelf not found under the NDK; set READELF to override"
  exit 2
fi

check_file() {
  local so="$1"
  local bad=0
  while read -r align; do
    [ -z "$align" ] && continue
    local dec
    dec=$(( align ))
    if [ "$dec" -lt "$ALIGN_REQUIRED" ]; then
      bad=1
    fi
  done < <("$READELF_BIN" -lW "$so" | awk '/  LOAD/{print $NF}')

  if [ "$bad" -ne 0 ]; then
    echo "alignment FAIL: $so carries a LOAD segment aligned below 16 KB (0x4000)"
    return 1
  fi
  echo "alignment ok: $so"
  return 0
}

status=0
checked=0

if [ -f "$TARGET" ]; then
  check_file "$TARGET" || status=1
  checked=1
elif [ -d "$TARGET" ]; then
  while IFS= read -r -d '' so; do
    check_file "$so" || status=1
    checked=1
  done < <(find "$TARGET" -type f -name '*.so' \( -path '*arm64-v8a*' -o -path '*x86_64*' \) -print0)
else
  echo "alignment: target not found: $TARGET"
  exit 2
fi

if [ "$checked" -eq 0 ]; then
  echo "alignment: no 64-bit .so found under $TARGET"
  exit 2
fi

if [ "$status" -ne 0 ]; then
  echo "alignment check failed"
  exit 1
fi

echo "alignment check passed"
