package com.remotex.feature.vnc.engine

import com.remotex.feature.vnc.domain.VncConnectionSpec
import com.remotex.feature.vnc.domain.VncFrame
import com.remotex.feature.vnc.domain.VncInputEvent
import com.remotex.feature.vnc.domain.VncSessionState
import com.remotex.feature.vnc.quality.VncPerformanceStats
import com.remotex.feature.vnc.quality.VncQualityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VncEngine {
    val state: StateFlow<VncSessionState>
    val frames: Flow<VncFrame>
    val remoteClipboard: Flow<String>
    val performanceStats: StateFlow<VncPerformanceStats>
    val qualityFallbacks: Flow<VncQualityMode>
    suspend fun connect(spec: VncConnectionSpec)
    suspend fun send(event: VncInputEvent)
    suspend fun setQualityMode(mode: VncQualityMode)
    suspend fun setFrameUpdatesEnabled(enabled: Boolean)
    suspend fun disconnect()
}
