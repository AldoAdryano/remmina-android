package com.remotex.feature.connections

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readBounded(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "File terlalu besar" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
