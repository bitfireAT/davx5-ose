/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCaptureTest {

    @Test
    fun logs_capturesRecords() {
        val capture = LogCapture(1000)
        capture.logger.info("hello from capture")
        assertTrue(capture.logs.contains("hello from capture"))
    }

    @Test
    fun logs_instancesAreIsolated() {
        val a = LogCapture(1000)
        val b = LogCapture(1000)
        a.logger.info("only in A")
        b.logger.info("only in B")
        assertTrue(a.logs.contains("only in A"))
        assertFalse(a.logs.contains("only in B"))
        assertTrue(b.logs.contains("only in B"))
        assertFalse(b.logs.contains("only in A"))
    }

}
