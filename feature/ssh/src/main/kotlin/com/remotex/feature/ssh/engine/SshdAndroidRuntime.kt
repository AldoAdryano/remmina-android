package com.remotex.feature.ssh.engine

import java.nio.file.Path
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.util.io.PathUtils

/**
 * Installs the Android-specific filesystem hooks required by Apache MINA SSHD.
 *
 * Android does not guarantee the JVM properties `user.home` and `user.dir` that
 * MINA uses for its default SSH config/key discovery. This must run before the
 * first SshClient/ClientBuilder initialization.
 */
object SshdAndroidRuntime {
    fun configure(appFilesDir: Path) {
        val home = appFilesDir.toAbsolutePath().normalize()
        val localUser = System.getProperty("user.name")
            ?.takeIf { it.isNotBlank() }
            ?: "remotex"

        OsUtils.setAndroid(true)

        System.setProperty("user.name", localUser)
        OsUtils.setCurrentUser(localUser)

        System.setProperty("user.home", home.toString())
        PathUtils.setUserHomeFolderResolver { home }

        System.setProperty("user.dir", home.toString())
        OsUtils.setCurrentWorkingDirectoryResolver { home }
    }
}
