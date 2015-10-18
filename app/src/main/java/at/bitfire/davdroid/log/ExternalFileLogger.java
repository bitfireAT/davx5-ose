/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import android.content.Context;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import lombok.Getter;

public class ExternalFileLogger extends CustomLogger implements Closeable {

    @Getter protected final String name;

    protected final PrintWriter writer;

    public ExternalFileLogger(Context context, String fileName, boolean verbose) throws IOException {
        this.verbose = verbose;

        File dir = context.getExternalFilesDir(null);
        if (dir == null)
            throw new IOException("External media not available for log creation");

        File log = new File(dir, name = fileName);
        writer = new PrintWriter(log);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    protected void log(String prefix, String msg) {
        writer.write(prefix + msg + "\n");
    }

    @Override
    protected void log(String prefix, String format, Object arg) {
        writer.write(prefix + format.replace("{}", arg.toString()) + "\n");
    }

    @Override
    protected void log(String prefix, String format, Object arg1, Object arg2) {
        writer.write(prefix + format.replaceFirst("\\{\\}", arg1.toString()).replaceFirst("\\{\\}", arg2.toString()) + "\n");
    }

    @Override
    protected void log(String prefix, String format, Object... args) {
        String message = prefix;
        for (Object arg : args)
            format.replaceFirst("\\{\\}", arg.toString());
        writer.write(prefix + format + "\n");
    }

    @Override
    protected void log(String prefix, String msg, Throwable t) {
        writer.write(prefix + msg + " - EXCEPTION:");
        t.printStackTrace(writer);
        writer.write("CAUSED BY:\n");
        ExceptionUtils.printRootCauseStackTrace(t, writer);
    }

}
