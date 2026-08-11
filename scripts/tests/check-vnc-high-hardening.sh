#!/usr/bin/env bash
set -euo pipefail
QUALITY=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/VncQuality.kt
DECODER=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/TightDecoder.kt
ENGINE=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt

grep -q 'private var jpegBytes = ByteArray(0)' "$DECODER"
grep -q 'private var jpegPixels = IntArray(0)' "$DECODER"
grep -q 'private var jpegBitmap: Bitmap? = null' "$DECODER"
grep -q 'inBitmap = reusable' "$DECODER"
grep -q 'previousBitmap' "$DECODER"
! grep -q 'System\.gc' "$DECODER" "$ENGINE"
python3 - "$QUALITY" <<'PY'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text()
block=re.search(r'VncQualityMode\.HIGH -> VncQualityProfile\((.*?)\n\s*\)', s, re.S)
assert block, 'HIGH profile missing'
text=block.group(1)
assert 'tightJpegQuality = 8' in text, 'HIGH must cap Tight JPEG quality at 8'
assert 'tightCompressionLevel = 2' in text, 'HIGH compression level must stay bounded at 2'
print('High quality profile cap: OK')
PY
grep -q 'catch (oom: OutOfMemoryError)' "$ENGINE"
grep -q 'downgradeHighAfterMemoryPressure' "$ENGINE"
grep -q 'requestedEffectiveQuality = VncQualityMode.BALANCED' "$ENGINE"
grep -q 'Kualitas Tinggi terlalu berat' "$ENGINE"
grep -q 'qualityFallbacks' "$ENGINE"
grep -q 'engine.qualityFallbacks.collect' feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncViewModel.kt
echo 'VNC High hardening source check: OK'
