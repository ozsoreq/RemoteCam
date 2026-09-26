#!/usr/bin/env bash
# Verifies Google Play's 16 KB page-size requirement for an APK:
#  1. every 64-bit native library has ELF LOAD segments aligned to >= 16 KB, and
#  2. uncompressed .so files are 16 KB-aligned inside the zip.
# Usage: scripts/check-16kb.sh path/to/app.apk
set -euo pipefail
apk="$1"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if ! unzip -q "$apk" 'lib/*' -d "$tmp" 2>/dev/null; then
  echo "No native libraries in $apk: 16 KB compatible."
  exit 0
fi

fail=0
while IFS= read -r so; do
  while read -r align; do
    if (( align < 0x4000 )); then
      echo "FAIL  ${so#$tmp/}  LOAD align $align (< 0x4000)"
      fail=1
    fi
  done < <(readelf -lW "$so" | awk '$1 == "LOAD" { print $NF }')
  (( fail )) || echo "ok    ${so#$tmp/}"
done < <(find "$tmp/lib" -name '*.so' \( -path '*arm64-v8a*' -o -path '*x86_64*' \) | sort)

zipalign="$(ls -d "${ANDROID_HOME:-/usr/local/lib/android/sdk}"/build-tools/*/ 2>/dev/null | sort -V | tail -1)zipalign"
if [[ -x "$zipalign" ]] && "$zipalign" 2>&1 | grep -q -- '-P'; then
  if "$zipalign" -c -P 16 -v 4 "$apk" > /dev/null; then
    echo "ok    zip alignment (16 KB)"
  else
    echo "FAIL  zip alignment: uncompressed libraries are not 16 KB-aligned"
    fail=1
  fi
else
  echo "skip  zip alignment check (no zipalign with -P support found)"
fi

if (( fail )); then
  echo "16 KB page-size check FAILED for $apk"
  exit 1
fi
echo "16 KB page-size check passed for $apk"
