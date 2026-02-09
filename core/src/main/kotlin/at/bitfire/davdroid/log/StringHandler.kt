/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import at.bitfire.synctools.log.PlainTextFormatter
import com.google.common.base.Ascii
import java.util.logging.Handler
import java.util.logging.LogRecord

/**
 * Handler that writes log messages to a string buffer.
 *
 * @param maxSize Maximum size of the buffer. If the buffer exceeds this size, it will be truncated.
 */
class StringHandler(
    private val maxSize: Int
): Handler() {

    companion object {
        const val TRUNCATION_MARKER = "[...]"
    }

    val builder = StringBuilder()

    init {
        formatter = PlainTextFormatter.DEFAULT
    }

    override fun publish(record: LogRecord) {
        var text = formatter.format(record)

        val currentSize = builder.length
        val sizeLeft = maxSize - currentSize

        when {
            // Append the text if there is enough space
            sizeLeft > text.length ->
                builder.append(text)

            // Truncate the text if there is not enough space
            sizeLeft > TRUNCATION_MARKER.length -> {
                text = Ascii.truncate(text, maxSize - currentSize, TRUNCATION_MARKER)
                builder.append(text)
            }

            // Do nothing if the buffer is already full
        }
    }

    override fun flush() {}
    override fun close() {}

    override fun toString() = builder.toString()

}