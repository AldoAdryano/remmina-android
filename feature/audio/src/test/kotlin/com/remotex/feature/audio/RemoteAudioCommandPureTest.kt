package com.remotex.feature.audio

fun main() {
    check(RemoteAudioCommand.RATE_HZ == 48_000)
    check(RemoteAudioCommand.CHANNELS == 2)
    check(RemoteAudioCommand.command.contains("pactl"))
    check(RemoteAudioCommand.command.contains("parec"))
    check(RemoteAudioCommand.command.contains("--format=s16le"))
    check(RemoteAudioCommand.classifyFailure("REMOTEX_AUDIO_MISSING") == "Utilitas audio Linux belum tersedia. Jalankan: sudo apt install pulseaudio-utils")
    check(RemoteAudioCommand.classifyFailure("REMOTEX_AUDIO_NO_SINK") == "Output audio Linux tidak ditemukan")
    check(RemoteAudioCommand.classifyFailure("Connection reset").contains("Connection reset"))
    check(RemoteAudioCommand.classifyFailure("") == "Audio remote terputus")
    println("Remote audio command pure test: OK")
}
