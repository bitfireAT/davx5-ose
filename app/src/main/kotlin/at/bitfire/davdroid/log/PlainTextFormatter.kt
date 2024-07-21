/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Formatter
import java.util.logging.LogRecord

class PlainTextFormatter(
    private val withTime: Boolean,
    private val withSource: Boolean,
    private val padSource: Int = 30,
    private val withException: Boolean,
    private val lineSeparator: String?
): Formatter() {

    companion object {

        /**
         * Formatter intended for logcat output.
         */
        val LOGCAT = PlainTextFormatter(
            withTime = false,
            withSource = false,
            withException = false,
            lineSeparator = null
        )

        /**
         * Formatter intended for file output.
         */
        val DEFAULT = PlainTextFormatter(
            withTime = true,
            withSource = true,
            withException = true,
            lineSeparator = System.lineSeparator()
        )

        fun shortClassName(className: String) = className
            .replace(Regex("^at\\.bitfire\\.(dav|cert4an|dav4an|ical4an|vcard4an)droid\\."), ".")
            .replace(Regex("\\$.*$"), "")

        private fun stackTrace(ex: Throwable): String {
            val writer = StringWriter()
            ex.printStackTrace(PrintWriter(writer))
            return writer.toString()
        }

    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)


    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (withTime)
            builder .append(timeFormat.format(Date(r.millis)))
                    .append(" ").append(r.threadID).append(" ")

        if (withSource && r.sourceClassName != null) {
            val className = shortClassName(r.sourceClassName)
            if (className != r.loggerName) {
                val classNameColumn = "[$className] ".padEnd(padSource)
                builder.append(classNameColumn)
            }
        }

        builder.append(r.message)

        if (withException && r.thrown != null) {
            val indentedStackTrace = stackTrace(r.thrown)
                .replace("\n", "\n\t")
                .removeSuffix("\t")
            builder.append("\n\tEXCEPTION ").append(indentedStackTrace)
        }

        r.parameters?.let {
            for ((idx, param) in it.withIndex())
                builder.append("\n\tPARAMETER #").append(idx).append(" = ").append(param)
        }

        if (lineSeparator != null)
            builder.append(lineSeparator)

        return builder.toString()
    }

}