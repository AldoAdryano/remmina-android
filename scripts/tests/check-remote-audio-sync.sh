#!/usr/bin/env bash
set -euo pipefail
POLICY=feature/audio/src/main/kotlin/com/remotex/feature/audio/AudioSyncPolicy.kt
PLAYER=feature/audio/src/main/kotlin/com/remotex/feature/audio/AndroidPcmPlayer.kt
TYPES=feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioTypes.kt
APP=app/src/main/kotlin/com/remotex/android/RemoteXApp.kt
[[ -f "$POLICY" ]] || { echo 'AudioSyncPolicy missing'; exit 1; }
grep -q 'initialDelayMs' "$PLAYER"
grep -q 'prebuffer' "$PLAYER"
grep -q 'Playing(val delayMs: Int)' "$TYPES"
grep -q 'AudioSyncPolicy.delayMsForFps' "$APP"
echo 'Remote audio sync source check: OK'
