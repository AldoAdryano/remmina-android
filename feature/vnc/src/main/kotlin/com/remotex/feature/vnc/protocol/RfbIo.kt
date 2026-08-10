package com.remotex.feature.vnc.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

internal fun DataInputStream.readU8(): Int = readUnsignedByte()
internal fun DataInputStream.readU16(): Int = readUnsignedShort()
internal fun DataInputStream.readU32(): Long = readInt().toLong() and 0xffffffffL
internal fun DataInputStream.readS32(): Int = readInt()

internal fun DataInputStream.readLengthPrefixedText(maxBytes: Int = 1_048_576): String {
    val length = readU32()
    require(length <= maxBytes) { "RFB text payload is too large: $length bytes" }
    val bytes = ByteArray(length.toInt())
    readFully(bytes)
    return bytes.toString(StandardCharsets.UTF_8)
}

internal fun DataOutputStream.writeU16(value: Int) = writeShort(value and 0xffff)
internal fun DataOutputStream.writeU32(value: Long) = writeInt((value and 0xffffffffL).toInt())
