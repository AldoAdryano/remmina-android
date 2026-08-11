#!/usr/bin/env bash
set -euo pipefail
SETTINGS=settings.gradle.kts
VERSIONS=gradle/libs.versions.toml
APP=app/src/main/kotlin/com/remotex/android/RemoteXApp.kt
SURFACE=app/src/main/kotlin/com/remotex/android/WatchPlayerSurface.kt
COMMAND=feature/watch/src/main/kotlin/com/remotex/feature/watch/RemoteWatchCommand.kt
PIPE=feature/watch/src/main/kotlin/com/remotex/feature/watch/RemoteWatchPipe.kt
CONTROLLER=feature/watch/src/main/kotlin/com/remotex/feature/watch/RemoteWatchController.kt
DATASOURCE=feature/watch/src/main/kotlin/com/remotex/feature/watch/SshPipeDataSource.kt
VNC_SCREEN=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt
VNC_ENGINE=feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt
MANIFEST=app/src/main/AndroidManifest.xml
UNIT_TEST=feature/watch/src/test/kotlin/com/remotex/feature/watch/RemoteWatchUnitTest.kt

grep -q '":feature:watch"' "$SETTINGS"
grep -q 'media3 = "1.10.1"' "$VERSIONS"
grep -q 'media3-exoplayer' "$VERSIONS"
grep -q 'media3-ui' "$VERSIONS"
grep -q 'RemoteWatchController' "$APP"
grep -q 'WatchPlayerSurface' "$APP"
grep -q 'audioVm.stop()' "$APP"
grep -q 'setFrameUpdatesEnabled(!mediaPlaneActive)' "$APP"
grep -q 'ToolButton("Menonton")\|else -> "Menonton"' "$VNC_SCREEN"
grep -q 'Keluar Menonton' "$VNC_SCREEN"
grep -q 'setFrameUpdatesEnabled' "$VNC_ENGINE"
grep -q 'ffmpeg' "$COMMAND"
grep -q 'x11grab' "$COMMAND"
grep -q '\.monitor' "$COMMAND"
grep -q 'libx264' "$COMMAND"
grep -q 'c:a aac' "$COMMAND"
grep -q 'setpts=PTS-STARTPTS' "$COMMAND"
grep -q 'asetpts=PTS-STARTPTS' "$COMMAND"
grep -q 'flush_packets 1' "$COMMAND"
grep -q 'f mpegts pipe:1' "$COMMAND"
grep -q 'class RemoteWatchPipe' "$PIPE"
grep -q 'DefaultLoadControl.Builder' "$CONTROLLER"
grep -q 'MIN_BUFFER_MS = 2_500' "$CONTROLLER"
grep -q 'MAX_BUFFER_MS = 5_000' "$CONTROLLER"
grep -q 'BUFFER_FOR_PLAYBACK_MS = 1_800' "$CONTROLLER"
grep -q 'MimeTypes.VIDEO_MP2T' "$CONTROLLER"
grep -q 'ProgressiveMediaSource.Factory' "$CONTROLLER"
grep -q 'class SshPipeDataSource' "$DATASOURCE"
grep -q 'PlayerView' "$SURFACE"
! grep -q 'RECORD_AUDIO' "$MANIFEST"
grep -q 'class RemoteWatchUnitTest' "$UNIT_TEST"
grep -q '@Test' "$UNIT_TEST"

if command -v kotlinc >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  kotlinc \
    "$COMMAND" \
    feature/watch/src/test/kotlin/com/remotex/feature/watch/RemoteWatchCommandPureTest.kt \
    -include-runtime -d "$TMP/watch-command.jar" >/dev/null
  java -jar "$TMP/watch-command.jar"
  kotlinc \
    "$PIPE" \
    feature/watch/src/test/kotlin/com/remotex/feature/watch/RemoteWatchPipePureTest.kt \
    -include-runtime -d "$TMP/watch-pipe.jar" >/dev/null
  java -jar "$TMP/watch-pipe.jar"
  cat > "$TMP/PrintCommand.kt" <<'KOT'
import com.remotex.feature.watch.RemoteWatchCommand
fun main() = print(RemoteWatchCommand.build())
KOT
  kotlinc "$COMMAND" "$TMP/PrintCommand.kt" -include-runtime -d "$TMP/print-command.jar" >/dev/null
  java -jar "$TMP/print-command.jar" > "$TMP/watch-command.sh"
  sh -n "$TMP/watch-command.sh"
  echo 'Watch FFmpeg shell syntax: OK'
fi

echo 'RemoteX Watch Mode source check: OK'
