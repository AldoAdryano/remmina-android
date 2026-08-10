#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

output="$(./scripts/check-secrets.sh 2>&1)"
printf '%s\n' "$output"

grep -Fq 'Secret scan: OK' <<<"$output"
