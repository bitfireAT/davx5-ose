/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log

import java.util.logging.Handler;
import java.util.logging.LogRecord;

class StringHandler: Handler() {

    val builder = StringBuilder()

    init {
        formatter = PlainTextFormatter.DEFAULT
    }

    override fun publish(record: LogRecord) {
        builder.append(formatter.format(record))
    }

    override fun flush() {}
    override fun close() {}

    override fun toString() = builder.toString()

}
