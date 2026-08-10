package com.remotex.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionValidatorTest {
    private val validator = ConnectionValidator()

    @Test
    fun blankHost_isRejected() {
        val errors = validator.validate(ConnectionProfile.new(name = "Jetson", host = "", username = "user"))
        assertTrue(errors.any { it.field == "host" })
    }

    @Test
    fun invalidPorts_areRejected() {
        val errors = validator.validate(
            ConnectionProfile.new(
                name = "Jetson",
                host = "192.168.1.10",
                username = "user",
                vncPort = 70000,
                sshPort = 0,
            )
        )
        assertEquals(setOf("vncPort", "sshPort"), errors.map { it.field }.toSet())
    }

    @Test
    fun validProfile_hasNoErrors() {
        val errors = validator.validate(
            ConnectionProfile.new(
                name = "Jetson",
                host = "192.168.1.10",
                username = "user",
                vncEnabled = true,
                sshEnabled = true,
            )
        )
        assertTrue(errors.isEmpty())
    }
}
