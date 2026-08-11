#!/usr/bin/env bash
set -euo pipefail

required=(
  README.md
  CHANGELOG.md
  PRIVACY.md
  docs/PANDUAN_PENGGUNAAN.md
  docs/RELEASE_CHECKLIST.md
  docs/releases/v1.0.0-rc1.md
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || { echo "Missing release file: $file" >&2; exit 1; }
done

grep -q 'v1.0.0-rc1' CHANGELOG.md
grep -q 'Desktop (VNC)' docs/PANDUAN_PENGGUNAAN.md
grep -q 'Mode Menonton' docs/PANDUAN_PENGGUNAAN.md
grep -q 'Ekspor profil' docs/PANDUAN_PENGGUNAAN.md
grep -q 'REMOTEX_KEYSTORE_BASE64' docs/RELEASE_CHECKLIST.md
grep -q 'notes-file' .github/workflows/release.yml
grep -q 'check-release-readiness.sh' .github/workflows/release.yml

echo 'Release readiness source check: OK'
