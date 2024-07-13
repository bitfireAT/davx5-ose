/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import android.os.Build
import android.util.Log
import com.google.common.base.Ascii

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

object LogcatHandler: Handler() {

    init {
        formatter = PlainTextFormatter.LOGCAT
    }

    override fun publish(r: LogRecord) {
        val level = r.level.intValue()
        val text = formatter.format(r)

        val className = PlainTextFormatter.shortClassName(r.sourceClassName)
        val tag = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            Ascii.truncate(className, 23, "")
        else
            className

        when {
            level >= Level.SEVERE.intValue()  -> Log.e(tag, text, r.thrown)
            level >= Level.WARNING.intValue() -> Log.w(tag, text, r.thrown)
            level >= Level.CONFIG.intValue()  -> Log.i(tag, text, r.thrown)
            level >= Level.FINER.intValue()   -> Log.d(tag, text, r.thrown)
            else                              -> Log.v(tag, text, r.thrown)
        }
    }

    override fun flush() {}
    override fun close() {}

}
