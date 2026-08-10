#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

ssh_engine="$root/feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaSshEngine.kt"
terminal="$root/feature/ssh/src/main/kotlin/com/remotex/feature/ssh/presentation/TerminalScreen.kt"
vnc="$root/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt"

failed=0

if grep -Fq 'override suspend fun close() = withContext(Dispatchers.IO)' "$ssh_engine"; then
  echo "FAIL: MinaSessionHandle.close uses expression body; withContext infers Result<CloseFuture> instead of Unit"
  failed=1
fi

if grep -Fq '.height(' "$terminal" && ! grep -Fq 'import androidx.compose.foundation.layout.height' "$terminal"; then
  echo "FAIL: TerminalScreen uses Modifier.height without importing layout.height"
  failed=1
fi

if grep -Fq 'import androidx.compose.ui.input.key.nativeKeyEvent' "$vnc"; then
  echo "FAIL: VncScreen imports nativeKeyEvent as an extension; it is a KeyEvent member property"
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

echo "Compile regression source check: OK"
