/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import com.google.common.base.Ascii
import java.util.logging.Handler
import java.util.logging.LogRecord

class StringHandler(
    private val maxSize: Int
): Handler() {

    val builder = StringBuilder()

    init {
        formatter = PlainTextFormatter.DEFAULT
    }

    override fun publish(record: LogRecord) {
        var text = formatter.format(record)

        val currentSize = builder.length
        if (currentSize + text.length > maxSize)
            text = Ascii.truncate(text, maxSize - currentSize, "[...]")

        builder.append(text)
    }

    override fun flush() {}
    override fun close() {}

    override fun toString() = builder.toString()

}