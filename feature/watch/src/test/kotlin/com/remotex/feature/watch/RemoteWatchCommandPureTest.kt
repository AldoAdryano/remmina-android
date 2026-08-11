package com.remotex.feature.watch

fun main() {
    val command = RemoteWatchCommand.build()
    check(command.contains("command -v ffmpeg"))
    check(command.contains("-f x11grab"))
    check(command.contains("-framerate 30"))
    check(command.contains("pactl info"))
    check(command.contains(".monitor"))
    check(command.contains("libx264"))
    check(command.contains("-c:a aac"))
    check(command.contains("scale=min(1280\\\\,iw):-2"))
    check(command.contains("setpts=PTS-STARTPTS"))
    check(command.contains("asetpts=PTS-STARTPTS"))
    check(command.contains("-flush_packets 1"))
    check(command.contains("-f mpegts pipe:1"))
    check(command.contains("REMOTEX_WATCH_MISSING_FFMPEG"))
    check(command.contains("/run/user/${'$'}uid/gdm/Xauthority"))
    check(command.contains("XAUTHORITY="))
    check(command.contains("awk \"/dimensions:/{print \\${'$'}2; exit}\""))
    println("RemoteWatchCommand pure test: OK")
}
