#!/usr/bin/env bash
set -euo pipefail
ENGINE=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt
QUALITY=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/VncQuality.kt
DECODER=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/TightDecoder.kt
CODEC=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/TightCodec.kt
SURFACE=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt
[[ -f "$DECODER" && -f "$CODEC" ]] || { echo 'Tight decoder files missing'; exit 1; }
grep -q 'ENCODING_TIGHT' "$ENGINE"
grep -q 'TightDecoder' "$ENGINE"
grep -q 'tightJpegQuality' "$QUALITY"
grep -q 'ENCODING_QUALITY_LEVEL_0' "$ENGINE"
grep -q 'ENCODING_COMPRESS_LEVEL_0' "$ENGINE"
grep -q 'MAX_COMPRESSED_BYTES' "$DECODER"
grep -q 'MAX_JPEG_BYTES' "$DECODER"
grep -q 'inJustDecodeBounds = true' "$DECODER"
grep -q 'snapshotDirtyRegion' "$ENGINE"
! grep -q 'framebuffer.copyOf()' "$ENGINE"
grep -q 'frame.argb.size == dirtyWidth \* dirtyHeight' "$SURFACE"
python3 - "$ENGINE" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
request=s.index('requestFramebufferUpdate(output, incremental = !qualityChanged)', s.index('private suspend fun readFramebufferUpdate'))
snapshot=s.index('snapshotDirtyRegion(', s.index('private suspend fun readFramebufferUpdate'))
assert request < snapshot, 'next framebuffer request must be sent before UI framebuffer copy'
print('VNC Tight video pipeline ordering: OK')
PY
if command -v kotlinc >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  cat > "$TMP/Main.kt" <<'KOT'
import com.remotex.feature.vnc.protocol.TightCodec
import java.io.ByteArrayInputStream
import java.io.DataInputStream

fun main() {
    fun compact(vararg values: Int) = TightCodec.readCompactLength(
        DataInputStream(ByteArrayInputStream(values.map(Int::toByte).toByteArray()))
    )
    check(compact(0x7f) == 0x7f)
    check(compact(0x80, 0x01) == 0x80)
    check(compact(0x80, 0x80, 0x01) == 0x4000)
    val palette = TightCodec.expandPalette(byteArrayOf(0xA0.toByte()), 3, 1, intArrayOf(1, 2))
    check(palette.contentEquals(intArrayOf(2, 1, 2)))
    val gradient = TightCodec.reconstructGradient24(
        byteArrayOf(10,20,30,10,10,10,5,5,5,5,5,5), 2, 2
    )
    check(gradient.contentEquals(intArrayOf(
        0xff0a141e.toInt(), 0xff141e28.toInt(),
        0xff0f1923.toInt(), 0xff1e2832.toInt()
    )))
    println("Tight codec pure check: OK")
}
KOT
  kotlinc "$CODEC" "$TMP/Main.kt" -include-runtime -d "$TMP/test.jar" >/dev/null
  java -jar "$TMP/test.jar"
fi

echo 'VNC Tight video source check: OK'
