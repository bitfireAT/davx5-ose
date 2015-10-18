/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import java.io.PrintWriter;
import java.io.StringWriter;

import lombok.Getter;

public class StringLogger extends CustomLogger {

    @Getter protected final String name;

    protected final StringWriter stringWriter = new StringWriter();


    public StringLogger(String name, boolean verbose) {
        this.name = name;
        this.verbose = verbose;

        writer = new PrintWriter(stringWriter);
    }

    public String toString() {
        return stringWriter.toString();
    }


}
