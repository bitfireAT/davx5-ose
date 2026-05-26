/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.log

import android.os.Build
import android.util.Log
import com.google.common.base.Ascii
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Logging handler that logs to Android logcat.
 *
 * Log level mapping: https://source.android.com/docs/core/tests/debug/understanding-logging#log-standards
 *
 * @param fallbackTag   adb tag to use if class name can't be determined
 */
class LogcatHandler(
    private val fallbackTag: String
): Handler() {

    val logcatFormatter = PlainTextFormatter.LOGCAT

    init {
        setFormatter(logcatFormatter)
    }

    override fun publish(r: LogRecord) {
        val level = r.level.intValue()
        val text = logcatFormatter.format(r)

        // log tag has to be truncated to 23 characters on Android <8, see Log documentation
        val tagLimited = Build.VERSION.SDK_INT < Build.VERSION_CODES.O

        // get class name that calls the logger (or fall back to package name)
        val tag = if (r.sourceClassName != null)
            ClassNameUtils.shortenClassName(r.sourceClassName, classNameFirst = tagLimited)
        else
            fallbackTag

        val tagOrTruncated = if (tagLimited)
            Ascii.truncate(tag, 23, "")
        else
            tag

        when {
            level >= Level.SEVERE.intValue() ->
                Log.e(tagOrTruncated, text, r.thrown)

            level >= Level.WARNING.intValue() ->
                Log.w(tagOrTruncated, text, r.thrown)

            level >= Level.INFO.intValue() ->
                Log.i(tagOrTruncated, text, r.thrown)

            level >= Level.FINE.intValue() ->   // CONFIG, FINE
                Log.d(tagOrTruncated, text, r.thrown)

            else ->                             // FINER, FINEST
                Log.v(tagOrTruncated, text, r.thrown)
        }
    }

    override fun flush() {}
    override fun close() {}

}