#!/usr/bin/env bash
set -euo pipefail
screen="feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt"
mapper="feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/KeySymMapper.kt"

grep -q 'const val RETURN = 0xff0d' "$mapper"
grep -q "'\\\\n', '\\\\r' -> dispatchKey(KeySymMapper.RETURN)" "$screen"
grep -q 'KeyboardActions(' "$screen"
for action in onDone onGo onNext onSearch onSend; do
  grep -q "$action = { dispatchKey(KeySymMapper.RETURN) }" "$screen"
done
grep -q 'ToolButton("Enter")' "$screen"

echo "VNC soft keyboard Enter source check: OK"
