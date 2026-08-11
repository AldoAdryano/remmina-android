package com.remotex.feature.watch

sealed interface RemoteWatchState {
    data object Idle : RemoteWatchState
    data object Connecting : RemoteWatchState
    data object Buffering : RemoteWatchState
    data class Playing(val label: String = "720p30") : RemoteWatchState
    data class Failed(val reason: String) : RemoteWatchState
}
