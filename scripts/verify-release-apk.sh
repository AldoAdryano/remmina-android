#!/usr/bin/env bash
set -euo pipefail

apk="${1:-}"
if [[ -z "$apk" || ! -f "$apk" ]]; then
  echo "Usage: $0 path/to/app-release.apk" >&2
  exit 2
fi

apksigner_bin="${APKSIGNER:-}"
if [[ -z "$apksigner_bin" ]]; then
  if command -v apksigner >/dev/null 2>&1; then
    apksigner_bin="$(command -v apksigner)"
  elif [[ -n "${ANDROID_HOME:-}" ]]; then
    apksigner_bin="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print 2>/dev/null | sort -V | tail -1)"
  fi
fi

if [[ -z "$apksigner_bin" || ! -x "$apksigner_bin" ]]; then
  echo "ERROR: apksigner was not found." >&2
  exit 1
fi

"$apksigner_bin" verify --verbose --print-certs "$apk"
sha256sum "$apk"
echo "Release APK signature verification: OK"
