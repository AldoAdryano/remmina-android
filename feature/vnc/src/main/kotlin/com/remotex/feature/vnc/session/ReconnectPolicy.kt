package com.remotex.feature.vnc.session

class ReconnectPolicy {
    fun delayMillis(attempt: Int): Long? = when (attempt) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 4_000L
        else -> null
    }
}
