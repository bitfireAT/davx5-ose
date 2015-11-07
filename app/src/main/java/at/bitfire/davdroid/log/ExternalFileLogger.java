/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class ExternalFileLogger extends CustomLogger implements Closeable {

    public ExternalFileLogger(Context context, String fileName, boolean verbose) throws IOException {
        this.verbose = verbose;

        File dir = getDirectory(context);
        if (dir == null)
            throw new IOException("External media not available for log creation");

        name = StringUtils.remove(StringUtils.remove(fileName, File.pathSeparatorChar), File.separatorChar);

        File log = new File(dir, name);
        writer = new PrintWriter(log);
    }

    public static File getDirectory(Context context) {
        return context.getExternalFilesDir(null);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

}
