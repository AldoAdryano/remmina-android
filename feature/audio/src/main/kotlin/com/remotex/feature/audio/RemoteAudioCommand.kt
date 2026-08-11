package com.remotex.feature.audio

object RemoteAudioCommand {
    const val RATE_HZ = 48_000
    const val CHANNELS = 2

    val command: String = """sh -c 'command -v pactl >/dev/null 2>&1 && command -v parec >/dev/null 2>&1 || { echo REMOTEX_AUDIO_MISSING >&2; exit 127; }; uid=${'$'}(id -u); export XDG_RUNTIME_DIR=${'$'}{XDG_RUNTIME_DIR:-/run/user/${'$'}uid}; sink=${'$'}(LC_ALL=C pactl info | sed -n "s/^Default Sink: //p" | head -n 1); [ -n "${'$'}sink" ] || { echo REMOTEX_AUDIO_NO_SINK >&2; exit 2; }; exec parec --device="${'$'}{sink}.monitor" --format=s16le --rate=$RATE_HZ --channels=$CHANNELS'"""

    fun classifyFailure(stderr: String): String {
        val message = stderr.trim()
        return when {
            message.contains("REMOTEX_AUDIO_MISSING") ->
                "Utilitas audio Linux belum tersedia. Jalankan: sudo apt install pulseaudio-utils"
            message.contains("REMOTEX_AUDIO_NO_SINK") ->
                "Output audio Linux tidak ditemukan"
            message.isBlank() -> "Audio remote terputus"
            else -> "Audio remote: ${message.take(180)}"
        }
    }
}
