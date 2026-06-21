/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Captures JUL log records of a bounded operation into a retrievable string.
 * Use [logger] for logging and read [logs] when done.
 *
 * @param maxSize truncation cap in bytes
 */
class LogCapture(maxSize: Int) {

    private val handler = StringHandler(maxSize)

    /** Use this logger during the operation. Its capture is then in [logs]. */
    val logger: Logger = Logger.getAnonymousLogger().apply {
        level = Level.ALL
        useParentHandlers = true
        addHandler(handler)
    }

    /** Captured log records as a formatted string. */
    val logs: String get() = handler.toString()

}
