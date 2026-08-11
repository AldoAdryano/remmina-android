package com.remotex.feature.watch

import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue

class RemoteWatchPipe(maxChunks: Int = DEFAULT_MAX_CHUNKS) {
    private sealed interface Item {
        data class Data(val bytes: ByteArray) : Item
        data object End : Item
        data class Failure(val error: IOException) : Item
    }

    private val queue = ArrayBlockingQueue<Item>(maxChunks.coerceAtLeast(2))
    private var current: ByteArray? = null
    private var currentOffset = 0
    @Volatile private var closed = false

    fun offer(bytes: ByteArray) {
        if (bytes.isEmpty() || closed) return
        queue.put(Item.Data(bytes))
    }

    fun fail(error: IOException) {
        if (closed) return
        closed = true
        current = null
        currentOffset = 0
        queue.clear()
        queue.offer(Item.Failure(error))
    }

    fun close() {
        if (closed) return
        closed = true
        queue.put(Item.End)
    }

    fun abort() {
        closed = true
        current = null
        currentOffset = 0
        queue.clear()
        queue.offer(Item.End)
    }

    @Throws(IOException::class)
    fun read(destination: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= destination.size)
        if (length == 0) return 0

        while (true) {
            val bytes = current
            if (bytes != null && currentOffset < bytes.size) {
                val count = minOf(length, bytes.size - currentOffset)
                bytes.copyInto(destination, offset, currentOffset, currentOffset + count)
                currentOffset += count
                if (currentOffset >= bytes.size) {
                    current = null
                    currentOffset = 0
                }
                return count
            }

            when (val item = queue.take()) {
                is Item.Data -> {
                    current = item.bytes
                    currentOffset = 0
                }
                Item.End -> return -1
                is Item.Failure -> throw item.error
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_CHUNKS = 96
    }
}
