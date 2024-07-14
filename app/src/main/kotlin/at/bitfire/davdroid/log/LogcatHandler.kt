/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import android.os.Build
import android.util.Log
import at.bitfire.davdroid.BuildConfig
import com.google.common.base.Ascii
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Logging handler that logs to Android logcat.
 */
internal class LogcatHandler: Handler() {

    init {
        formatter = PlainTextFormatter.LOGCAT
    }

    override fun publish(r: LogRecord) {
        val level = r.level.intValue()
        val text = formatter.format(r)

        // get class name that calls the logger (or fall back to package name)
        val className = if (r.sourceClassName != null)
            PlainTextFormatter.shortClassName(r.sourceClassName)
        else
            BuildConfig.APPLICATION_ID

        // truncate class name to 23 characters on Android <8, see Log documentation
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