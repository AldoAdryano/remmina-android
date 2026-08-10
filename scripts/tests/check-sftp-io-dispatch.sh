#!/usr/bin/env bash
set -euo pipefail

file="feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaSftpTransport.kt"

fail() {
  echo "SFTP IO dispatch check FAILED: $1" >&2
  exit 1
}

grep -q 'import kotlinx.coroutines.Dispatchers' "$file" || fail "Dispatchers import missing"
grep -q 'import kotlinx.coroutines.withContext' "$file" || fail "withContext import missing"

# Every synchronous Apache MINA SFTP call must live behind Dispatchers.IO.
for signature in \
  'override suspend fun list' \
  'override suspend fun exists' \
  'override suspend fun mkdir' \
  'override suspend fun rename' \
  'override suspend fun removeFile' \
  'override suspend fun removeDirectory' \
  'override suspend fun openRead' \
  'override suspend fun openWrite' \
  'override suspend fun close'; do
  line="$(grep -nF "$signature" "$file" | head -1 | cut -d: -f1 || true)"
  [[ -n "$line" ]] || fail "missing method: $signature"
  window="$(sed -n "${line},$((line + 4))p" "$file")"
  grep -q 'withContext(Dispatchers.IO)' <<<"$window" || fail "$signature is not dispatched to Dispatchers.IO"
done

echo "SFTP IO dispatch source check: OK"
