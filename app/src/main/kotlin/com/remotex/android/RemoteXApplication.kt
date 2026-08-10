package com.remotex.android

import android.app.Application
import com.remotex.feature.ssh.engine.SshdAndroidRuntime

class RemoteXApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        SshdAndroidRuntime.configure(filesDir.toPath())
    }
}
