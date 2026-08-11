#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCREEN="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt"
SURFACE="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt"
TYPES="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/domain/VncTypes.kt"

require() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! grep -Fq "$pattern" "$file"; then
    echo "FAIL: $message" >&2
    exit 1
  fi
}

require 'mutableStateOf(true)' "$SCREEN" 'VNC should enter fullscreen by default'
require 'var controlsVisible by remember' "$SCREEN" 'floating controls visibility state is missing'
require 'delay(3_500)' "$SCREEN" 'controls should auto-hide after 3.5 seconds'
require 'VncControlsHandle(' "$SCREEN" 'persistent controls handle is missing'
require 'AnimatedVisibility(' "$SCREEN" 'floating control strip should animate visibility'
require 'Modifier.fillMaxSize()' "$SCREEN" 'framebuffer should own the full screen'

# Task 3 requirements are intentionally checked here too.
require 'FILL_SCREEN' "$TYPES" 'fill-screen scale mode is missing'
require 'VncScaleMode.FILL_SCREEN' "$SURFACE" 'surface does not render fill-screen mode'
require 'maxOf(vw / rw, vh / rh)' "$SURFACE" 'fill-screen should use center-crop max ratio'
require 'TrackpadGestureInterpreter(' "$SURFACE" 'surface should reuse trackpad interpreter'
require 'scaledTouchSlop' "$SURFACE" 'gesture classification should use Android touch slop'
require 'SCROLL_STEP_PX = 40f' "$SURFACE" 'scroll step should be 40 px'
require '"Isi Layar"' "$SCREEN" 'fill-screen label is missing from scale controls'

echo 'VNC polish source check: OK'
