package com.remotex.feature.watch

import java.io.IOException

fun main() {
    val pipe = RemoteWatchPipe(maxChunks = 2)
    pipe.offer(byteArrayOf(1, 2, 3))
    pipe.offer(byteArrayOf(4, 5))
    val out = ByteArray(4)
    val first = pipe.read(out, 0, 4)
    check(first == 3)
    check(out.copyOf(first).contentEquals(byteArrayOf(1, 2, 3)))
    val second = pipe.read(out, 0, 4)
    check(second == 2)
    check(out.copyOf(second).contentEquals(byteArrayOf(4, 5)))
    pipe.close()
    check(pipe.read(out, 0, 4) == -1)

    val failedPipe = RemoteWatchPipe(maxChunks = 2)
    failedPipe.offer(byteArrayOf(9, 9, 9))
    failedPipe.fail(IOException("boom"))
    val failure = runCatching { failedPipe.read(out, 0, out.size) }.exceptionOrNull()
    check(failure is IOException && failure.message == "boom")

    println("RemoteWatchPipe pure test: OK")
}
