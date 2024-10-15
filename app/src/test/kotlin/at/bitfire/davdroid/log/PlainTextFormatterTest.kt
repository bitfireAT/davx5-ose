/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import org.junit.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class PlainTextFormatterTest {

    @Test
    fun test_format_TruncatesMessage() {
        val formatter = PlainTextFormatter.DEFAULT
        val result = formatter.format(LogRecord(Level.INFO, "a".repeat(50000)))
        // PlainTextFormatter.MAX_LENGTH is 10,000, so the message should be truncated to 10,000 + something
        assert(result.length <= 10100)
    }

}