/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.log

import android.os.Build
import android.util.Log
import com.google.common.base.Ascii
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Logging handler that logs to Android logcat ([Log]).
 *
 * Maps log level according to [Android docs](https://source.android.com/docs/core/tests/debug/understanding-logging#log-standards).
 *
 * Logs source class and exception natively over logcat, so the formatter is configured
 * to omit those.
 */
class LogcatHandler : Handler() {

    init {
        formatter = PlainTextFormatter.LOGCAT
    }

    override fun publish(r: LogRecord) {
        val level = r.level.intValue()
        val text = formatter.format(r)

        // use class name (or fallbackTag, if not available) as logcat tag
        val tag = if (r.sourceClassName != null)
            ClassNameUtils.shortenClassName(r.sourceClassName, classNameFirst = tagLengthRestricted)
        else
            FALLBACK_TAG

        val tagOrTruncated = if (tagLengthRestricted)
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


    companion object {

        private const val FALLBACK_TAG: String = "at.bitfire"

        /** log tag has to be truncated to 23 characters on Android <8, see Log documentation */
        val tagLengthRestricted = Build.VERSION.SDK_INT < Build.VERSION_CODES.O

    }

}