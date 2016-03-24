/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class PlainTextFormatter extends Formatter {
    public final static PlainTextFormatter
            LOGCAT = new PlainTextFormatter(true),
            DEFAULT = new PlainTextFormatter(false);

    private final boolean logcat;

    private PlainTextFormatter(boolean onLogcat) {
        this.logcat = onLogcat;
    }

    @Override
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public String format(LogRecord r) {
        StringBuilder builder = new StringBuilder();

        if (!logcat) {
            builder.append(DateFormatUtils.format(r.getMillis(), "yyyy-MM-dd HH:mm:ss"));
            builder.append(String.format(" %d ", r.getThreadID()));
        }

        builder.append(String.format("[%s] %s", shortClassName(r.getSourceClassName()), r.getMessage()));

        if (r.getThrown() != null) {
            Throwable thrown = r.getThrown();
            builder.append("\nEXCEPTION ").append(ExceptionUtils.getMessage(thrown));
            builder.append("\tstrack trace = ").append(ExceptionUtils.getStackTrace(thrown));
        }

        if (r.getParameters() != null) {
            int idx = 1;
            for (Object param : r.getParameters())
                builder.append("\n\tPARAMETER #").append(idx).append(" = ").append(param);
        }

        if (!logcat)
            builder.append("\n");

        return builder.toString();
    }

    private String shortClassName(String className) {
        String s = StringUtils.replace(className, "at.bitfire.davdroid.", "");
        return StringUtils.replace(s, "at.bitfire.", "");
    }

}
