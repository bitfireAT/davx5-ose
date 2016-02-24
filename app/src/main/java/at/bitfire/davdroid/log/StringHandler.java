/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class StringHandler extends Handler {

    StringBuilder builder = new StringBuilder();

    public StringHandler() {
        super();
        setFormatter(PlainTextFormatter.DEFAULT);
    }

    @Override
    public void publish(LogRecord record) {
        builder.append(getFormatter().format(record));
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
