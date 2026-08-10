package com.remotex.feature.vnc.engine

import com.remotex.feature.vnc.domain.VncConnectionSpec
import com.remotex.feature.vnc.domain.VncFrame
import com.remotex.feature.vnc.domain.VncInputEvent
import com.remotex.feature.vnc.domain.VncSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VncEngine {
    val state: StateFlow<VncSessionState>
    val frames: Flow<VncFrame>
    val remoteClipboard: Flow<String>
    suspend fun connect(spec: VncConnectionSpec)
    suspend fun send(event: VncInputEvent)
    suspend fun disconnect()
}
