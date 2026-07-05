/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.log

import com.google.common.base.Ascii
import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Formatter
import java.util.logging.LogRecord

/**
 * Logging formatter for logging as formatted plain text.
 */
class PlainTextFormatter(
    private val withTimeAndThreadId: Boolean,
    private val withSource: Boolean,
    private val withException: Boolean,
    private val padSource: Int = 0,
    private val lineSeparator: String? = System.lineSeparator()
) : Formatter() {

    companion object {

        /**
         * Formatter intended for logcat output.
         */
        val FOR_LOGCAT = PlainTextFormatter(
            withTimeAndThreadId = false,    // logged by logcat, not needed in text
            withSource = false,             // source class is used as tag in LogcatHandler, not needed in text
            withException = false,          // exception is attached natively by LogcatHandler, not needed in text
            lineSeparator = null            // omit line separators for logcat
        )

        /**
         * Formatter intended for custom log file output.
         */
        val FOR_FILE = PlainTextFormatter(
            withTimeAndThreadId = true,
            withSource = true,
            withException = true,
            padSource = 35
        )

        /**
         * Maximum length of a log line (estimate).
         */
        const val MAX_LENGTH = 10000

    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)


    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (withTimeAndThreadId)
            builder
                .append(timeFormat.format(Date(r.millis)))
                .append(" ").append(r.threadID).append(" ")

        if (withSource && r.sourceClassName != null) {
            val className = ClassNameUtils.shortenClassName(r.sourceClassName, classNameFirst = false)
            if (className != r.loggerName) {
                val classNameColumn = "[$className] ".padEnd(padSource)
                builder.append(classNameColumn)
            }
        }

        val formattedMessage =
            if (r.parameters == null)
                r.message
            else
                try {
                    MessageFormat.format(r.message, *r.parameters)
                } catch (_: IllegalArgumentException) {
                    // fall back to message when it couldn't be parsed
                    r.message
                }
        builder.append(truncate(formattedMessage))

        if (withException && r.thrown != null) {
            val indentedStackTrace = stackTrace(r.thrown)
                .replace("\n", "\n\t")
                .removeSuffix("\t")
            builder.append("\n\tEXCEPTION ").append(indentedStackTrace)
        }

        if (lineSeparator != null)
            builder.append(lineSeparator)

        return builder.toString()
    }

    private fun stackTrace(ex: Throwable): String =
        StringWriter().run {
            ex.printStackTrace(PrintWriter(this))
            toString()
        }

    private fun truncate(s: String) =
        Ascii.truncate(s, MAX_LENGTH, "[…]")

}