package com.remotex.feature.connections

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedInputTest {
    @Test
    fun inputWithinLimit_isRead() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readBounded(3))
    }

    @Test
    fun oversizedInput_isRejected() {
        val failure = runCatching { ByteArrayInputStream(ByteArray(5)).readBounded(4) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
