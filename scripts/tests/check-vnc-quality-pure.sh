#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/Main.kt" <<'KOT'
import com.remotex.feature.vnc.quality.AdaptiveQualityController
import com.remotex.feature.vnc.quality.VncQualityMode
import com.remotex.feature.vnc.quality.profileFor

fun main() {
    check(VncQualityMode.BALANCED.profileFor().pixelFormat.bitsPerPixel == 32)
    check(VncQualityMode.PERFORMANCE.profileFor().pixelFormat.bitsPerPixel == 16)
    check(VncQualityMode.HIGH.profileFor().preferRaw)

    val auto = AdaptiveQualityController()
    check(auto.effectiveMode == VncQualityMode.BALANCED)
    auto.observeWindow(fps = 15, changedFrames = 15)
    check(auto.effectiveMode == VncQualityMode.BALANCED)
    auto.observeWindow(fps = 15, changedFrames = 15)
    check(auto.effectiveMode == VncQualityMode.PERFORMANCE)

    auto.observeWindow(fps = 30, changedFrames = 30)
    auto.observeWindow(fps = 30, changedFrames = 30)
    check(auto.effectiveMode == VncQualityMode.PERFORMANCE)
    auto.observeWindow(fps = 30, changedFrames = 30)
    check(auto.effectiveMode == VncQualityMode.BALANCED)

    val idle = AdaptiveQualityController()
    repeat(4) { idle.observeWindow(fps = 2, changedFrames = 2) }
    check(idle.effectiveMode == VncQualityMode.BALANCED)
    println("VNC quality pure check: OK")
}
KOT
kotlinc \
  "$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/RfbPixelFormat.kt" \
  "$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/VncQuality.kt" \
  "$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/AdaptiveQualityController.kt" \
  "$TMP/Main.kt" \
  -include-runtime -d "$TMP/test.jar"
java -jar "$TMP/test.jar"
