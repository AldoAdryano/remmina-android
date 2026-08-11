package com.remotex.feature.vnc.quality

class AdaptiveQualityController(
    private val slowFpsThreshold: Int = 18,
    private val healthyFpsThreshold: Int = 24,
    private val minimumActiveFrames: Int = 4,
    private val slowWindowsRequired: Int = 2,
    private val healthyWindowsRequired: Int = 3,
) {
    var effectiveMode: VncQualityMode = VncQualityMode.BALANCED
        private set

    private var slowWindows = 0
    private var healthyWindows = 0

    fun reset() {
        effectiveMode = VncQualityMode.BALANCED
        slowWindows = 0
        healthyWindows = 0
    }

    fun observeWindow(fps: Int, changedFrames: Int): VncQualityMode {
        if (changedFrames < minimumActiveFrames) {
            slowWindows = 0
            healthyWindows = 0
            return effectiveMode
        }

        when (effectiveMode) {
            VncQualityMode.BALANCED -> {
                healthyWindows = 0
                if (fps < slowFpsThreshold) {
                    slowWindows += 1
                    if (slowWindows >= slowWindowsRequired) {
                        effectiveMode = VncQualityMode.PERFORMANCE
                        slowWindows = 0
                    }
                } else {
                    slowWindows = 0
                }
            }

            VncQualityMode.PERFORMANCE -> {
                slowWindows = 0
                if (fps >= healthyFpsThreshold) {
                    healthyWindows += 1
                    if (healthyWindows >= healthyWindowsRequired) {
                        effectiveMode = VncQualityMode.BALANCED
                        healthyWindows = 0
                    }
                } else {
                    healthyWindows = 0
                }
            }

            else -> reset()
        }
        return effectiveMode
    }
}
