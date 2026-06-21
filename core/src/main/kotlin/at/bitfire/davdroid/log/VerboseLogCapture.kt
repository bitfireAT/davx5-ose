/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import at.bitfire.synctools.log.PlainTextFormatter
import java.io.Closeable
import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Captures JUL log records of a bounded operation into a file.
 * Use [logger] for logging and read [logFile] when done.
 *
 * Logs at [Level.ALL] and propagates to the parent logger (logcat etc.) as usual.
 *
 * Must be [close]d when the operation is finished to flush and release the file handle.
 *
 * @param logFile  file to write log records to; created/overwritten on construction
 */
class VerboseLogCapture(val logFile: File) : Closeable {

    private val fileHandler = FileHandler(logFile.absolutePath, 1_000_000, 1, false).apply {
        formatter = PlainTextFormatter.DEFAULT
    }

    /** Use this logger during the operation. Its records are written to [logFile]. */
    val logger: Logger = Logger.getAnonymousLogger().apply {
        // verbose logging
        level = Level.ALL

        // pass through to default handlers (adb logs)
        useParentHandlers = true

        // log into file
        addHandler(fileHandler)
    }

    override fun close() = fileHandler.close()

}
