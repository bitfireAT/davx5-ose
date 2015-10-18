/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.io.PrintWriter;

import lombok.Getter;

/**
 * A DAVdroid logger base class that wraps all calls around some standard log() calls.
 * @throws  UnsupportedOperationException for all methods with Marker
 *          arguments (as Markers are not used by DAVdroid logging).
 */
public abstract class CustomLogger implements Logger {

    private static final String
            PREFIX_ERROR = "[error] ",
            PREFIX_WARN  = "[warn ] ",
            PREFIX_INFO  = "[info ] ",
            PREFIX_DEBUG = "[debug] ",
            PREFIX_TRACE = "[trace] ";

    @Getter
    protected String name;

    protected PrintWriter writer;
    protected boolean verbose;


    // CUSTOM LOGGING METHODS

    protected void log(String prefix, String msg) {
        writer.write(prefix + msg + "\n");
    }

    protected void log(String prefix, String format, Object arg) {
        writer.write(prefix + format.replace("{}", arg.toString()) + "\n");
    }

    protected void log(String prefix, String format, Object arg1, Object arg2) {
        writer.write(prefix + format.replaceFirst("\\{\\}", arg1.toString()).replaceFirst("\\{\\}", arg2.toString()) + "\n");
    }

    protected void log(String prefix, String format, Object... args) {
        String message = prefix;
        for (Object arg : args)
            format.replaceFirst("\\{\\}", arg.toString());
        writer.write(prefix + format + "\n");
    }

    protected void log(String prefix, String msg, Throwable t) {
        writer.write(prefix + msg + " - EXCEPTION:\n");
        t.printStackTrace(writer);
        ExceptionUtils.printRootCauseStackTrace(t, writer);
    }


    // STANDARD CALLS

    @Override
    public boolean isTraceEnabled() {
        return verbose;
    }

    @Override
    public void trace(String msg) {
        if (verbose)
            log(PREFIX_TRACE, msg);
    }

    @Override
    public void trace(String format, Object arg) {
        if (verbose)
            log(PREFIX_TRACE, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (verbose) log(PREFIX_TRACE, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (verbose)
            log(PREFIX_TRACE, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (verbose)
            log(PREFIX_TRACE, msg, t);
    }


    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        log(PREFIX_DEBUG, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        log(PREFIX_DEBUG, format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        log(PREFIX_DEBUG, format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        log(PREFIX_DEBUG, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(PREFIX_DEBUG, msg, t);
    }


    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        log(PREFIX_INFO, msg);
    }

    @Override
    public void info(String format, Object arg) {
        log(PREFIX_INFO, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        log(PREFIX_INFO, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        log(PREFIX_INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(PREFIX_INFO, msg, t);
    }


    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        log(PREFIX_WARN, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        log(PREFIX_WARN, format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        log(PREFIX_WARN, format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        log(PREFIX_WARN, format, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(PREFIX_WARN, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        log(PREFIX_ERROR, msg);
    }

    @Override
    public void error(String format, Object arg) {
        log(PREFIX_ERROR, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        log(PREFIX_ERROR, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        log(PREFIX_ERROR, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(PREFIX_ERROR, msg, t);
    }


    // CALLS WITH MARKER

    @Override
    public boolean isTraceEnabled(Marker marker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trace(Marker marker, String msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void debug(Marker marker, String msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void info(Marker marker, String msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void warn(Marker marker, String msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void error(Marker marker, String msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        throw new UnsupportedOperationException();
    }
}
