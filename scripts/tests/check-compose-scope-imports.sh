#!/usr/bin/env bash
set -euo pipefail

if grep -RIn --include='*.kt' \
  'import androidx\.compose\.foundation\.layout\.weight' \
  feature app core; then
  echo 'ERROR: Do not explicitly import foundation.layout.weight with this Compose version; use RowScope/ColumnScope member extension in scope.' >&2
  exit 1
fi

echo 'Compose scope import check: OK'
