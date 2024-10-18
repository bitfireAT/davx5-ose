/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class PlainTextFormatterTest {

    private val minimum = PlainTextFormatter(
        withTime = false,
        withSource = false,
        withException = false,
        lineSeparator = null
    )

    @Test
    fun test_format_param_null() {
        val result = minimum.format(LogRecord(Level.INFO, "Message").apply {
            parameters = arrayOf(null)
        })
        assertEquals("Message\n\tPARAMETER #1 = (null)", result)
    }

    @Test
    fun test_format_param_object() {
        val result = minimum.format(LogRecord(Level.INFO, "Message").apply {
            parameters = arrayOf(object {
                override fun toString() = "SomeObject[]"
            })
        })
        assertEquals("Message\n\tPARAMETER #1 = SomeObject[]", result)
    }

    @Test
    fun test_format_truncatesMessage() {
        val result = minimum.format(LogRecord(Level.INFO, "a".repeat(50000)))
        // PlainTextFormatter.MAX_LENGTH is 10,000
        assertEquals(10000, result.length)
    }

}