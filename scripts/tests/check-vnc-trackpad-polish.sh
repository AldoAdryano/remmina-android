#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/TestMain.kt" <<'KOTLIN'
import com.remotex.feature.vnc.input.TrackpadGestureInterpreter
import com.remotex.feature.vnc.input.TrackpadResult

private fun assertEquals(expected: Any, actual: Any, label: String) {
    check(expected == actual) { "$label: expected=$expected actual=$actual" }
}

fun main() {
    val precise = TrackpadGestureInterpreter(
        pointerSpeed = 1f,
        acceleration = 0.5f,
        accelerationDistance = 40f,
    )

    assertEquals(
        TrackpadResult.Pointer(111, 100, 0),
        precise.move(1, 10f, 0f, 100, 100, 1920, 1080),
        "short swipe precision",
    )
    assertEquals(
        TrackpadResult.Pointer(160, 100, 0),
        precise.move(1, 40f, 0f, 100, 100, 1920, 1080),
        "long swipe acceleration",
    )
    assertEquals(
        TrackpadResult.Pointer(0, 0, 0),
        precise.move(1, -500f, -500f, 10, 10, 1920, 1080),
        "framebuffer clamp",
    )
    assertEquals(
        listOf(TrackpadResult.PointerButton(4), TrackpadResult.PointerButton(0)),
        precise.rightTap(),
        "right click mask",
    )

    println("VNC trackpad polish check: OK")
}
KOTLIN

kotlinc \
    "$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/TrackpadGestureInterpreter.kt" \
    "$TMP/TestMain.kt" \
    -include-runtime \
    -d "$TMP/test.jar"

java -jar "$TMP/test.jar"
