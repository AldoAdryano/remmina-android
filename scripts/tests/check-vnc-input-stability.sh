#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INTERPRETER="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/TrackpadGestureInterpreter.kt"
GUARD="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/TrackpadTouchGuard.kt"
SURFACE="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt"
SCREEN="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

require_pattern() {
  local pattern="$1" file="$2" label="$3"
  if ! grep -Fq -- "$pattern" "$file"; then
    echo "FAIL: $label" >&2
    exit 1
  fi
}

require_pattern 'pointerRemainderX' "$INTERPRETER" 'sub-pixel pointer remainder is missing'
require_pattern 'roundToInt()' "$INTERPRETER" 'symmetric pointer rounding is missing'
require_pattern 'fun scroll(' "$INTERPRETER" 'pure natural-scroll mapping is missing'
require_pattern 'scrollAccumulatorY -= dy' "$INTERPRETER" 'vertical scroll direction is not inverted/natural'
require_pattern 'class TrackpadTouchGuard' "$GUARD" 'multi-touch transition guard is missing'
require_pattern 'MotionEvent.ACTION_POINTER_UP' "$SURFACE" 'pointer-up transition is not explicitly handled'
require_pattern 'touchGuard.canMovePointer' "$SURFACE" 'single-finger movement is not guarded after multi-touch'
require_pattern 'trackpad.scroll(' "$SURFACE" 'surface still duplicates scroll mapping'
require_pattern 'ToolButton("Putar")' "$SCREEN" 'manual orientation control is missing'
require_pattern 'SCREEN_ORIENTATION_SENSOR_PORTRAIT' "$SCREEN" 'portrait orientation target is missing'
require_pattern 'SCREEN_ORIENTATION_SENSOR_LANDSCAPE' "$SCREEN" 'landscape orientation target is missing'
require_pattern 'android:configChanges="orientation|screenSize"' "$MANIFEST" 'orientation would recreate MainActivity and drop/lock the remote session'

echo 'VNC input stability source check: OK'
