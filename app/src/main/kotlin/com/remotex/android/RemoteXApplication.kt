package com.remotex.android

import android.app.Application

class RemoteXApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
