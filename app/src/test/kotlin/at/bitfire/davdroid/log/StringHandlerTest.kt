/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord

class StringHandlerTest {

    @Test
    fun test_logSomeText() {
        val handler = StringHandler(1000)
        handler.publish(LogRecord(Level.INFO, "Line 1"))
        handler.publish(LogRecord(Level.FINEST, "Line 2"))
        val str = handler.toString()
        assertTrue(str.contains("Line 1\n"))
        assertTrue(str.contains("Line 2\n"))
    }

    @Test
    fun test_logSomeText_ExceedingMaxSize() {
        val handler = StringHandler(10).apply {
            formatter = object: Formatter() {
                override fun format(record: LogRecord) = record.message
            }
        }
        handler.publish(LogRecord(Level.INFO, "Line 1 Line 1 Line 1 Line 1 Line 1"))
        handler.publish(LogRecord(Level.FINEST, "Line 2"))

        val str = handler.toString()
        assertEquals(10, handler.toString().length)
        assertEquals("Line [...]", str)
    }

}