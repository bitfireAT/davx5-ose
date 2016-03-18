/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import android.util.Log;

import org.apache.commons.lang3.math.NumberUtils;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class LogcatHandler extends Handler {
    private static final int MAX_LINE_LENGTH = 3000;
    public static final LogcatHandler INSTANCE = new LogcatHandler();

    private LogcatHandler() {
        super();
        setFormatter(PlainTextFormatter.LOGCAT);
        setLevel(Level.ALL);
    }

    @Override
    public void publish(LogRecord r) {
        String text = getFormatter().format(r);
        int level = r.getLevel().intValue();

        int end = text.length();
        for (int pos = 0; pos < end; pos += MAX_LINE_LENGTH) {
            String line = text.substring(pos, NumberUtils.min(pos + MAX_LINE_LENGTH, end));

            if (level >= Level.SEVERE.intValue())
                Log.e(r.getLoggerName(), line);
            else if (level >= Level.WARNING.intValue())
                Log.w(r.getLoggerName(), line);
            else if (level >= Level.CONFIG.intValue())
                Log.i(r.getLoggerName(), line);
            else if (level >= Level.FINER.intValue())
                Log.d(r.getLoggerName(), line);
            else
                Log.v(r.getLoggerName(), line);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

}
