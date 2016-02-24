/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import android.util.Log;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class LogcatHandler extends Handler {
    private static final String TAG = "davdroid";

    public static final LogcatHandler INSTANCE = new LogcatHandler();

    private LogcatHandler() {
        super();
        setFormatter(AdbFormatter.INSTANCE);
        setLevel(Level.ALL);
    }

    @Override
    public void publish(LogRecord record) {
        String line = getFormatter().format(record);
        int level = record.getLevel().intValue();

        if (level >= Level.SEVERE.intValue())
            Log.e(TAG, line);
        else if (level >= Level.WARNING.intValue())
            Log.w(TAG, line);
        else if (level >= Level.CONFIG.intValue())
            Log.i(TAG, line);
        else if (level >= Level.FINER.intValue())
            Log.d(TAG, line);
        else
            Log.v(TAG, line);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }


    private static class AdbFormatter extends Formatter {
        public static AdbFormatter INSTANCE = new AdbFormatter();

        private AdbFormatter() {
        }

        @Override
        public String format(LogRecord r) {
            return String.format("[%s] %s", r.getSourceClassName(), r.getMessage());
        }
    }

}
