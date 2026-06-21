/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerboseLogCaptureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun logFile_capturesRecords() {
        val logFile = tempFolder.newFile()
        VerboseLogCapture(logFile).use { capture ->
            capture.logger.info("hello from capture")
        }
        assertTrue(logFile.readText().contains("hello from capture"))
    }

    @Test
    fun logFile_instancesAreIsolated() {
        val fileA = tempFolder.newFile()
        val fileB = tempFolder.newFile()
        VerboseLogCapture(fileA).use { it.logger.info("only in A") }
        VerboseLogCapture(fileB).use { it.logger.info("only in B") }
        assertTrue(fileA.readText().contains("only in A"))
        assertFalse(fileA.readText().contains("only in B"))
        assertTrue(fileB.readText().contains("only in B"))
        assertFalse(fileB.readText().contains("only in A"))
    }

}
