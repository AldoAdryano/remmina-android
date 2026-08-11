#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SETTINGS="$ROOT/settings.gradle.kts"
APP_GRADLE="$ROOT/app/build.gradle.kts"
SSH_TYPES="$ROOT/feature/ssh/src/main/kotlin/com/remotex/feature/ssh/domain/SshTypes.kt"
SSH_ENGINE="$ROOT/feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaSshEngine.kt"
EXEC_IMPL="$ROOT/feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaExecChannel.kt"
AUDIO_TYPES="$ROOT/feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioTypes.kt"
AUDIO_COMMAND="$ROOT/feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioCommand.kt"
AUDIO_PLAYER="$ROOT/feature/audio/src/main/kotlin/com/remotex/feature/audio/AndroidPcmPlayer.kt"
AUDIO_ENGINE="$ROOT/feature/audio/src/main/kotlin/com/remotex/feature/audio/SshPcmAudioEngine.kt"
AUDIO_VM="$ROOT/feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioViewModel.kt"
APP="$ROOT/app/src/main/kotlin/com/remotex/android/RemoteXApp.kt"
VNC_SCREEN="$ROOT/feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

require_file() {
  [[ -f "$1" ]] || { echo "FAIL: missing $2" >&2; exit 1; }
}
require_pattern() {
  local pattern="$1" file="$2" label="$3"
  grep -Fq -- "$pattern" "$file" || { echo "FAIL: $label" >&2; exit 1; }
}
reject_pattern() {
  local pattern="$1" file="$2" label="$3"
  if grep -Fq -- "$pattern" "$file"; then echo "FAIL: $label" >&2; exit 1; fi
}

require_pattern '":feature:audio"' "$SETTINGS" 'audio module is not included'
require_pattern 'implementation(project(":feature:audio"))' "$APP_GRADLE" 'app does not depend on audio feature'
require_pattern 'interface ExecChannel' "$SSH_TYPES" 'SSH exec channel contract is missing'
require_pattern 'openExec(command: String): ExecChannel' "$SSH_TYPES" 'SSH session cannot open exec streams'
require_file "$EXEC_IMPL" 'MinaExecChannel.kt'
require_pattern 'createExecChannel(command)' "$SSH_ENGINE" 'MINA SSH engine does not open exec channel'
require_pattern 'setUsePty(false)' "$SSH_ENGINE" 'audio exec channel must not allocate a PTY'

require_file "$AUDIO_TYPES" 'RemoteAudioTypes.kt'
require_file "$AUDIO_COMMAND" 'RemoteAudioCommand.kt'
require_file "$AUDIO_PLAYER" 'AndroidPcmPlayer.kt'
require_file "$AUDIO_ENGINE" 'SshPcmAudioEngine.kt'
require_file "$AUDIO_VM" 'RemoteAudioViewModel.kt'
require_pattern 'RATE_HZ = 48_000' "$AUDIO_COMMAND" 'remote audio rate must be 48 kHz'
require_pattern 'CHANNELS = 2' "$AUDIO_COMMAND" 'remote audio must be stereo'
require_pattern 'REMOTEX_AUDIO_MISSING' "$AUDIO_COMMAND" 'missing PulseAudio utilities marker is absent'
require_pattern 'AudioTrack.MODE_STREAM' "$AUDIO_PLAYER" 'Android playback is not streaming AudioTrack'
require_pattern 'AudioFormat.ENCODING_PCM_16BIT' "$AUDIO_PLAYER" 'Android playback is not PCM16'
require_pattern 'AudioManager.AUDIOFOCUS_GAIN' "$AUDIO_PLAYER" 'audio focus is not requested'
require_pattern 'openExec(RemoteAudioCommand.command)' "$AUDIO_ENGINE" 'audio does not stream through SSH exec channel'
require_pattern 'RemoteAudioState.Playing' "$AUDIO_ENGINE" 'playing state is missing'

require_pattern 'RemoteAudioViewModel' "$APP" 'VNC route does not own remote audio'
require_pattern 'savedSshAuth(profile)' "$APP" 'saved SSH credentials are not reused for audio'
require_pattern 'Aktifkan SSH pada profil untuk audio' "$APP" 'SSH-disabled audio guard is missing'
require_pattern 'audioPlaying = audioState is RemoteAudioState.Playing' "$APP" 'audio playing state is not supplied to VNC screen'
require_pattern 'onAudioToggle' "$VNC_SCREEN" 'VNC toolbar has no audio callback'
require_pattern '"Suara"' "$VNC_SCREEN" 'audio start label is missing'
require_pattern '"Bisukan"' "$VNC_SCREEN" 'audio stop label is missing'
reject_pattern 'RECORD_AUDIO' "$MANIFEST" 'FIX 12 must not request microphone permission'

# Shell command must be fixed: no host, username, or password interpolation in capture command.
reject_pattern '${host}' "$AUDIO_COMMAND" 'capture command interpolates host'
reject_pattern '${username}' "$AUDIO_COMMAND" 'capture command interpolates username'

if command -v kotlinc >/dev/null 2>&1; then
  TMP_JAR="$(mktemp --suffix=.jar)"
  trap 'rm -f "$TMP_JAR"' EXIT
  kotlinc \
    "$AUDIO_COMMAND" \
    "$ROOT/feature/audio/src/test/kotlin/com/remotex/feature/audio/RemoteAudioCommandPureTest.kt" \
    -include-runtime -d "$TMP_JAR" >/dev/null
  java -jar "$TMP_JAR" >/dev/null
else
  echo 'Remote audio pure test: SKIP (kotlinc tidak tersedia)'
fi

require_pattern 'TARGET_BUFFER_MS = 60' "$AUDIO_PLAYER" 'audio target buffer must be 60 ms'
require_pattern 'AudioTrack.PERFORMANCE_MODE_LOW_LATENCY' "$AUDIO_PLAYER" 'AudioTrack low-latency performance mode is missing'
require_pattern 'const val BUFFER_SIZE = 4 * 1024' "$EXEC_IMPL" 'SSH exec PCM chunks should be 4 KiB'

printf '%s\n' 'Remote audio source check: OK'
