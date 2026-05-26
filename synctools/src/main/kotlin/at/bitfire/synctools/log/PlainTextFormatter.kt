/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.log

import com.google.common.base.Ascii
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Formatter
import java.util.logging.LogRecord

/**
 * Logging formatter for logging as formatted plain text.
 */
class PlainTextFormatter(
    private val withTime: Boolean,
    private val withSource: Boolean,
    private val padSource: Int = 0,
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
            padSource = 35,
            withException = true,
            lineSeparator = System.lineSeparator()
        )

        /**
         * Maximum length of a log line (estimate).
         */
        const val MAX_LENGTH = 10000

    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)


    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (withTime)
            builder .append(timeFormat.format(Date(r.millis)))
                    .append(" ").append(r.threadID).append(" ")

        if (withSource && r.sourceClassName != null) {
            val className = ClassNameUtils.shortenClassName(r.sourceClassName, classNameFirst = false)
            if (className != r.loggerName) {
                val classNameColumn = "[$className] ".padEnd(padSource)
                builder.append(classNameColumn)
            }
        }

        builder.append(truncate(r.message))

        if (withException && r.thrown != null) {
            val indentedStackTrace = stackTrace(r.thrown)
                .replace("\n", "\n\t")
                .removeSuffix("\t")
            builder.append("\n\tEXCEPTION ").append(indentedStackTrace)
        }

        r.parameters?.let {
            for ((idx, param) in it.withIndex()) {
                builder.append("\n\tPARAMETER #").append(idx + 1).append(" = ")

                val valStr = if (param == null)
                    "(null)"
                else
                    truncate(param.toString())
                builder.append(valStr)
            }
        }

        if (lineSeparator != null)
            builder.append(lineSeparator)

        return builder.toString()
    }

    fun shortClassName(className: String): String {
        // remove $... that is appended for anonymous classes
        val withoutSuffix = className.replace(Regex("\\$.*$"), "")

        // shorten all but the last part of the package name
        val parts = withoutSuffix.split('.')
        val shortened =
            if (parts.isNotEmpty()) {
                val lastIdx = parts.size - 1
                val shortenedParts = parts.mapIndexed { idx, part ->
                    if (idx == lastIdx)
                        part
                    else
                        part[0]
                }
                shortenedParts.joinToString(".")
            } else
                ""

        return shortened
    }

    private fun stackTrace(ex: Throwable): String {
        val writer = StringWriter()
        ex.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun truncate(s: String) =
        Ascii.truncate(s, MAX_LENGTH, "[…]")

}