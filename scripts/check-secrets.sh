#!/usr/bin/env bash
set -euo pipefail

forbidden_globs=(
  '*.jks'
  '*.keystore'
  '*.pem'
  '*.key'
  '.env'
  '.env.*'
  'secrets.properties'
  'local.properties'
  'remotex-profiles*.json'
  'connections-export*.json'
)

for pattern in "${forbidden_globs[@]}"; do
  if git ls-files -- "$pattern" ':(glob)**/'"$pattern" | grep -q .; then
    echo "ERROR: forbidden secret-like file is tracked: $pattern" >&2
    git ls-files -- "$pattern" ':(glob)**/'"$pattern" >&2 || true
    exit 1
  fi
done

private_key_pattern='BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY'
password_assignment='(password|passphrase|token|secret)[[:space:]]*[:=][[:space:]]*["'"'][^"'"']{4,}["'"']'

if git grep -nEI "$private_key_pattern" -- \
  ':!README.md' ':!SECURITY.md' ':!docs/**' ':!scripts/check-secrets.sh'; then
  echo "ERROR: private-key material may be committed." >&2
  exit 1
fi

if git grep -nEI "$password_assignment" -- \
  ':!README.md' ':!SECURITY.md' ':!docs/**' ':!scripts/check-secrets.sh' ':!**/src/test/**'; then
  echo "ERROR: possible hard-coded credential detected." >&2
  exit 1
fi

# These values were used only during the early manual Jetson discussion and
# must never enter this public source repository.
for literal in '192\.168\.10\.67' 'werkudhara'; do
  if git grep -nEI "$literal" -- ':!docs/superpowers/**' ':!scripts/check-secrets.sh'; then
    echo "ERROR: personal Jetson connection data detected in source." >&2
    exit 1
  fi
done

echo "Secret scan: OK"
