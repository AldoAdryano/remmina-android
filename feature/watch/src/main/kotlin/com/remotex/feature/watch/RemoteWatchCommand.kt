package com.remotex.feature.watch

object RemoteWatchCommand {
    const val WIDTH = 1280
    const val FPS = 30
    const val VIDEO_CRF = 20
    const val AUDIO_BITRATE = "160k"

    fun build(): String = """sh -c '
set -eu
command -v ffmpeg >/dev/null 2>&1 || { echo REMOTEX_WATCH_MISSING_FFMPEG >&2; exit 127; }
command -v xdpyinfo >/dev/null 2>&1 || { echo REMOTEX_WATCH_MISSING_XDPYINFO >&2; exit 127; }
command -v pactl >/dev/null 2>&1 || { echo REMOTEX_WATCH_MISSING_PACTL >&2; exit 127; }
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q "libx264" || { echo REMOTEX_WATCH_MISSING_X264 >&2; exit 127; }
uid=${'$'}(id -u)
export XDG_RUNTIME_DIR=${'$'}{XDG_RUNTIME_DIR:-/run/user/${'$'}uid}
auth="${'$'}{XAUTHORITY:-}"
display=""
for candidate in "${'$'}{DISPLAY:-}" :0 :1; do
  [ -n "${'$'}candidate" ] || continue
  if DISPLAY="${'$'}candidate" xdpyinfo >/dev/null 2>&1; then
    display="${'$'}candidate"
    break
  fi
done
if [ -z "${'$'}display" ]; then
  for authCandidate in "${'$'}auth" "${'$'}HOME/.Xauthority" "/run/user/${'$'}uid/gdm/Xauthority"; do
    [ -n "${'$'}authCandidate" ] && [ -r "${'$'}authCandidate" ] || continue
    for candidate in "${'$'}{DISPLAY:-}" :0 :1; do
      [ -n "${'$'}candidate" ] || continue
      if DISPLAY="${'$'}candidate" XAUTHORITY="${'$'}authCandidate" xdpyinfo >/dev/null 2>&1; then
        display="${'$'}candidate"
        auth="${'$'}authCandidate"
        break 2
      fi
    done
  done
fi
[ -n "${'$'}display" ] || { echo REMOTEX_WATCH_NO_DISPLAY >&2; exit 2; }
export DISPLAY="${'$'}display"
if [ -n "${'$'}auth" ]; then export XAUTHORITY="${'$'}auth"; fi
dims=${'$'}(xdpyinfo | awk "/dimensions:/{print \${'$'}2; exit}")
[ -n "${'$'}dims" ] || { echo REMOTEX_WATCH_NO_DIMENSIONS >&2; exit 2; }
sink=${'$'}(LC_ALL=C pactl info | sed -n "s/^Default Sink: //p" | head -n 1)
[ -n "${'$'}sink" ] || { echo REMOTEX_WATCH_NO_SINK >&2; exit 2; }
exec ffmpeg -hide_banner -loglevel error -nostdin \
  -thread_queue_size 1024 -f x11grab -framerate 30 -video_size "${'$'}dims" -i "${'$'}display" \
  -thread_queue_size 1024 -f pulse -i "${'$'}{sink}.monitor" \
  -vf "setpts=PTS-STARTPTS,scale=min(1280\\,iw):-2:flags=lanczos,format=yuv420p" \
  -c:v libx264 -preset veryfast -tune zerolatency -profile:v high -level 4.0 -crf 20 \
  -g 60 -keyint_min 60 -sc_threshold 0 -vsync 1 \
  -c:a aac -b:a 160k -ar 48000 -ac 2 -af "aresample=async=1000:first_pts=0,asetpts=PTS-STARTPTS" \
  -muxdelay 0 -muxpreload 0 -mpegts_flags +resend_headers -flush_packets 1 -f mpegts pipe:1
'""".trimIndent()

    fun classifyFailure(stderr: String): String = when {
        "REMOTEX_WATCH_MISSING_FFMPEG" in stderr -> "Mode Menonton memerlukan FFmpeg. Jalankan: sudo apt install ffmpeg"
        "REMOTEX_WATCH_MISSING_XDPYINFO" in stderr -> "Mode Menonton memerlukan xdpyinfo (x11-utils)"
        "REMOTEX_WATCH_MISSING_PACTL" in stderr -> "Mode Menonton memerlukan pactl (pulseaudio-utils)"
        "REMOTEX_WATCH_MISSING_X264" in stderr -> "FFmpeg remote tidak memiliki encoder H.264 libx264"
        "REMOTEX_WATCH_NO_DISPLAY" in stderr -> "Display X11 remote tidak ditemukan"
        "REMOTEX_WATCH_NO_DIMENSIONS" in stderr -> "Ukuran desktop remote tidak dapat dibaca"
        "REMOTEX_WATCH_NO_SINK" in stderr -> "Output audio remote tidak ditemukan"
        stderr.isNotBlank() -> "Mode Menonton gagal: ${stderr.lineSequence().first().take(180)}"
        else -> "Mode Menonton berhenti"
    }
}
