package com.remotex.feature.vnc

import com.remotex.feature.vnc.quality.AdaptiveQualityController
import com.remotex.feature.vnc.quality.VncQualityMode
import com.remotex.feature.vnc.quality.profileFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VncQualityTest {
    @Test
    fun balancedRestoresFullColorWhilePerformanceKeepsRgb565() {
        assertEquals(32, VncQualityMode.BALANCED.profileFor().pixelFormat.bitsPerPixel)
        assertEquals(16, VncQualityMode.PERFORMANCE.profileFor().pixelFormat.bitsPerPixel)
        assertFalse(VncQualityMode.BALANCED.profileFor().preferRaw)
        assertTrue(VncQualityMode.HIGH.profileFor().preferRaw)
    }

    @Test
    fun autoUsesHysteresisAndIgnoresIdleWindows() {
        val controller = AdaptiveQualityController()
        repeat(4) { controller.observeWindow(fps = 2, changedFrames = 2) }
        assertEquals(VncQualityMode.BALANCED, controller.effectiveMode)

        controller.observeWindow(fps = 15, changedFrames = 15)
        controller.observeWindow(fps = 15, changedFrames = 15)
        assertEquals(VncQualityMode.PERFORMANCE, controller.effectiveMode)

        repeat(2) { controller.observeWindow(fps = 30, changedFrames = 30) }
        assertEquals(VncQualityMode.PERFORMANCE, controller.effectiveMode)
        controller.observeWindow(fps = 30, changedFrames = 30)
        assertEquals(VncQualityMode.BALANCED, controller.effectiveMode)
    }
}
