/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.log

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogRecord

class PlainTextFormatter private constructor(
        private val logcat: Boolean
): Formatter() {

    companion object {
        val LOGCAT = PlainTextFormatter(true)
        val DEFAULT = PlainTextFormatter(false)

        const val MAX_MESSAGE_LENGTH = 20000
    }

    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (!logcat)
            builder .append(DateFormatUtils.format(r.millis, "yyyy-MM-dd HH:mm:ss", Locale.ROOT))
                    .append(" ").append(r.threadID).append(" ")

        val className = shortClassName(r.sourceClassName)
        if (className != r.loggerName)
            builder.append("[").append(className).append("] ")

        builder.append(StringUtils.abbreviate(r.message, MAX_MESSAGE_LENGTH))

        r.thrown?.let {
            builder .append("\nEXCEPTION ")
                    .append(ExceptionUtils.getStackTrace(it))
        }

        r.parameters?.let {
            for ((idx, param) in it.withIndex())
                builder.append("\n\tPARAMETER #").append(idx).append(" = ").append(param)
        }

        if (!logcat)
            builder.append("\n")

        return builder.toString()
    }

    private fun shortClassName(className: String) = className
            .replace(Regex("^at\\.bitfire\\.(dav|cert4an|dav4an|ical4an|vcard4an)droid\\."), "")
            .replace(Regex("\\$.*$"), "")

}
