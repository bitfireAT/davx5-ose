/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import java.util.Locale
import java.util.logging.Formatter
import java.util.logging.LogRecord

class PlainTextFormatter private constructor(
    private val forLogcat: Boolean
): Formatter() {

    companion object {

        /**
         * Formatter intended for logcat output.
         */
        val LOGCAT = PlainTextFormatter(true)

        /**
         * Formatter intended for file output.
         */
        val DEFAULT = PlainTextFormatter(false)

        fun shortClassName(className: String) = className
            .replace(Regex("^at\\.bitfire\\.(dav|cert4an|dav4an|ical4an|vcard4an)droid\\."), ".")
            .replace(Regex("\\$.*$"), "")

    }


    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (!forLogcat) {
            builder .append(DateFormatUtils.format(r.millis, "yyyy-MM-dd HH:mm:ss", Locale.ROOT))
                    .append(" ").append(r.threadID).append(" ")

            if (r.sourceClassName != null) {
                val className = shortClassName(r.sourceClassName)
                if (className != r.loggerName)
                    builder.append("[").append(className).append("] ")
            }
        }

        builder.append(r.message)

        if (!forLogcat)
            r.thrown?.let {
                builder .append("\nEXCEPTION ")
                        .append(ExceptionUtils.getStackTrace(it))
            }

        r.parameters?.let {
            for ((idx, param) in it.withIndex())
                builder.append("\n\tPARAMETER #").append(idx).append(" = ").append(param)
        }

        if (!forLogcat)
            builder.append("\n")

        return builder.toString()
    }

}