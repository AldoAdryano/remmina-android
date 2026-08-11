#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PIXEL="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/RfbPixelFormat.kt"
HEXTILE="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/HextileDecoder.kt"
ENGINE="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt"
SURFACE="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt"

require_pattern() {
  local pattern="$1" file="$2" label="$3"
  grep -Fq -- "$pattern" "$file" || { echo "FAIL: $label" >&2; exit 1; }
}

[[ -f "$HEXTILE" ]] || { echo 'FAIL: HextileDecoder.kt missing' >&2; exit 1; }
require_pattern 'fun remoteXPerformance()' "$PIXEL" 'RGB565 performance pixel format missing'
require_pattern 'bitsPerPixel = 16' "$PIXEL" 'performance pixel format is not 16-bit'
require_pattern 'ENCODING_HEXTILE' "$ENGINE" 'Hextile encoding is not advertised/handled'
require_pattern 'HextileDecoder(pixelFormat)' "$ENGINE" 'RFB engine does not use Hextile decoder'
require_pattern '.setPixels(' "$SURFACE" 'surface is not updating a persistent bitmap'
require_pattern 'postInvalidateOnAnimation()' "$SURFACE" 'surface rendering is not vsync-scheduled'
require_pattern 'snapshotDirtyRegion(' "$ENGINE" 'engine must snapshot only the changed framebuffer region'
require_pattern 'postInvalidateOnAnimation()' "$SURFACE" 'surface rendering is not vsync-scheduled'

if command -v kotlinc >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  cat > "$TMP/TestMain.kt" <<'KOTLIN'
import com.remotex.feature.vnc.protocol.HextileDecoder
import com.remotex.feature.vnc.protocol.RfbPixelFormat
import java.io.ByteArrayInputStream
import java.io.DataInputStream

private fun assertEquals(expected: Int, actual: Int, label: String) {
    check(expected == actual) { "$label: expected=${expected.toUInt().toString(16)} actual=${actual.toUInt().toString(16)}" }
}

fun main() {
    val format = RfbPixelFormat.remoteXPerformance()
    assertEquals(0xffff0000.toInt(), format.decodePixel(byteArrayOf(0x00, 0xF8.toByte()), 0), "RGB565 red")
    assertEquals(0xff00ff00.toInt(), format.decodePixel(byteArrayOf(0xE0.toByte(), 0x07), 0), "RGB565 green")
    assertEquals(0xff0000ff.toInt(), format.decodePixel(byteArrayOf(0x1F, 0x00), 0), "RGB565 blue")

    // One 2x2 Hextile tile: background red, one coloured 1x1 blue subrect at (1,1).
    val encoded = byteArrayOf(
        (2 or 8 or 16).toByte(), // BackgroundSpecified | AnySubrects | SubrectsColoured
        0x00, 0xF8.toByte(),    // red RGB565
        0x01,                   // one subrect
        0x1F, 0x00,             // blue RGB565
        0x11,                   // x=1 y=1
        0x00,                   // w=1 h=1
    )
    val fb = IntArray(4)
    HextileDecoder(format).decodeRectangle(
        input = DataInputStream(ByteArrayInputStream(encoded)),
        framebuffer = fb,
        framebufferWidth = 2,
        framebufferHeight = 2,
        x = 0,
        y = 0,
        width = 2,
        height = 2,
    )
    assertEquals(0xffff0000.toInt(), fb[0], "hextile background top-left")
    assertEquals(0xffff0000.toInt(), fb[1], "hextile background top-right")
    assertEquals(0xffff0000.toInt(), fb[2], "hextile background bottom-left")
    assertEquals(0xff0000ff.toInt(), fb[3], "hextile coloured subrect")
    println("VNC performance pure check: OK")
}
KOTLIN
  kotlinc "$PIXEL" "$HEXTILE" "$TMP/TestMain.kt" -include-runtime -d "$TMP/test.jar" >/dev/null
  java -jar "$TMP/test.jar"
else
  echo 'VNC performance pure check: SKIP (kotlinc/java unavailable)'
fi

echo 'VNC performance source check: OK'
