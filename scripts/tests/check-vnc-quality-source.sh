#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
QUALITY="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/VncQuality.kt"
AUTO="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/AdaptiveQualityController.kt"
ENGINE="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt"
VM="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncViewModel.kt"
SCREEN="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt"
WORKFLOW="$ROOT/.github/workflows/build-debug.yml"

require_pattern() {
  local pattern="$1" file="$2" label="$3"
  grep -Fq -- "$pattern" "$file" || { echo "FAIL: $label" >&2; exit 1; }
}

require_pattern 'enum class VncQualityMode' "$QUALITY" 'quality mode enum missing'
require_pattern 'VncQualityMode.AUTO' "$QUALITY" 'Auto profile mapping missing'
require_pattern 'RfbPixelFormat.remoteXDefault()' "$QUALITY" 'full-color balanced/high mapping missing'
require_pattern 'RfbPixelFormat.remoteXPerformance()' "$QUALITY" 'RGB565 performance mapping missing'
require_pattern 'class AdaptiveQualityController' "$AUTO" 'adaptive quality controller missing'
require_pattern 'slowWindowsRequired: Int = 2' "$AUTO" 'slow-window hysteresis missing'
require_pattern 'healthyWindowsRequired: Int = 3' "$AUTO" 'healthy-window hysteresis missing'
require_pattern 'requestedEffectiveQuality' "$ENGINE" 'engine does not queue quality changes'
require_pattern 'applyPendingQuality(output)' "$ENGINE" 'quality change is not applied at framebuffer boundary'
require_pattern 'incremental = !qualityChanged' "$ENGINE" 'quality switch must force non-incremental refresh'
require_pattern 'hextileDecoder = HextileDecoder(pixelFormat)' "$ENGINE" 'decoder is not recreated for new pixel format'
require_pattern 'sendSetEncodings(output, desired.profileFor().preferRaw)' "$ENGINE" 'encoding preference is not profile-specific'
require_pattern 'VncQualityMode.BALANCED' "$VM" 'ViewModel default is not balanced'
require_pattern 'fun setQualityMode(mode: VncQualityMode)' "$VM" 'ViewModel quality setter missing'
require_pattern 'DropdownMenu(' "$SCREEN" 'quality dropdown menu missing'
require_pattern '"Kualitas: ${qualityMode.label()}"' "$SCREEN" 'quality toolbar label missing'
require_pattern 'performanceStats.fps' "$SCREEN" 'FPS indicator missing'
require_pattern '"Otomatis · ${performanceStats.activeQuality.label()}"' "$SCREEN" 'Auto effective-mode indicator missing'
require_pattern 'Check VNC adaptive display quality' "$WORKFLOW" 'GitHub Actions quality regression step missing'

echo 'VNC adaptive quality source check: OK'
