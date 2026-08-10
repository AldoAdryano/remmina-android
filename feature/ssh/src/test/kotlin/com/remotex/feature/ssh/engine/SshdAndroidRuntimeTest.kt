package com.remotex.feature.ssh.engine

import java.nio.file.Files
import java.nio.file.Path
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.util.io.PathUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SshdAndroidRuntimeTest {
    private var previousUserHome: String? = null
    private var previousUserDir: String? = null
    private var previousUserName: String? = null
    private lateinit var tempHome: Path

    @Before
    fun setUp() {
        previousUserHome = System.getProperty("user.home")
        previousUserDir = System.getProperty("user.dir")
        previousUserName = System.getProperty("user.name")
        tempHome = Files.createTempDirectory("remotex-sshd-home")

        System.clearProperty("user.home")
        System.clearProperty("user.dir")
        System.clearProperty("user.name")
        PathUtils.setUserHomeFolderResolver(null)
        OsUtils.setCurrentWorkingDirectoryResolver(null)
        OsUtils.setCurrentUser(null)
        OsUtils.setAndroid(null)
    }

    @After
    fun tearDown() {
        restoreProperty("user.home", previousUserHome)
        restoreProperty("user.dir", previousUserDir)
        restoreProperty("user.name", previousUserName)
        PathUtils.setUserHomeFolderResolver(null)
        OsUtils.setCurrentWorkingDirectoryResolver(null)
        OsUtils.setCurrentUser(null)
        OsUtils.setAndroid(null)
        Files.deleteIfExists(tempHome)
    }

    @Test
    fun configure_providesAndroidHomeCwdAndLocalUser() {
        SshdAndroidRuntime.configure(tempHome)

        assertEquals(tempHome.toAbsolutePath().normalize(), PathUtils.getUserHomeFolder())
        assertEquals(tempHome.toAbsolutePath().normalize(), OsUtils.getCurrentWorkingDirectory())
        assertEquals("remotex", OsUtils.getCurrentUser())
        assertTrue(OsUtils.isAndroid())
    }

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
}
