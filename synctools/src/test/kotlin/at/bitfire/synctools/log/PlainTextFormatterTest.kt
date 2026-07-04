/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.log

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class PlainTextFormatterTest {

    private val minimum = PlainTextFormatter(
        withTimeAndThreadId = false,
        withSource = false,
        padSource = 0,
        withException = false,
        lineSeparator = null
    )

    @Test
    fun `format with invalid message formatting`() {
        val result = minimum.format(LogRecord(Level.INFO, "Message {!}").apply {
            parameters = arrayOf(null)
        })
        assertEquals("Message {!}", result)
    }

    @Test
    fun `format with null parameter`() {
        val result = minimum.format(LogRecord(Level.INFO, "Message {0}").apply {
            parameters = arrayOf(null)
        })
        assertEquals("Message null", result)
    }

    @Test
    fun `format with Object parameter`() {
        val result = minimum.format(LogRecord(Level.INFO, "Message {0}").apply {
            parameters = arrayOf(object {
                override fun toString() = "SomeObject[]"
            })
        })
        assertEquals("Message SomeObject[]", result)
    }

    @Test
    fun `format truncates long line`() {
        val result = minimum.format(LogRecord(Level.INFO, "a".repeat(50000)))
        // PlainTextFormatter.MAX_LENGTH is 10,000
        assertEquals(10000, result.length)
    }

}