/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.log

import android.util.Log

import org.apache.commons.lang3.math.NumberUtils

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

object LogcatHandler: Handler() {

    private const val MAX_LINE_LENGTH = 3000

    init {
        formatter = PlainTextFormatter.LOGCAT
        level = Level.ALL
    }

    override fun publish(r: LogRecord) {
        val text = formatter.format(r)
        val level = r.level.intValue()

        val end = text.length
        var pos = 0
        while (pos < end) {
            val line = text.substring(pos, NumberUtils.min(pos + MAX_LINE_LENGTH, end))
            when {
                level >= Level.SEVERE.intValue()  -> Log.e(r.loggerName, line)
                level >= Level.WARNING.intValue() -> Log.w(r.loggerName, line)
                level >= Level.CONFIG.intValue()  -> Log.i(r.loggerName, line)
                level >= Level.FINER.intValue()   -> Log.d(r.loggerName, line)
                else                              -> Log.v(r.loggerName, line)
            }
            pos += MAX_LINE_LENGTH
        }
    }

    override fun flush() {}
    override fun close() {}

}
