/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import at.bitfire.synctools.log.PlainTextFormatter
import com.google.errorprone.annotations.MustBeClosed
import java.io.Closeable
import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.WillCloseWhenClosed

/** Factory for bounded file-based logging captures. */
object FileLoggerFactory {

    /**
     * Holds a [Logger] and its backing [FileHandler] for a bounded operation.
     *
     * Must be [close]d when done!
     */
    class FileLoggerContext(
        val logger: Logger,
        @WillCloseWhenClosed private val fileHandler: FileHandler
    ) : Closeable {
        override fun close() = fileHandler.close()
    }

    /**
     * Creates a [FileLoggerContext] that writes log records to [file] (up to 1 MB,
     * overwriting) and propagates them to the parent logger (logcat etc.).
     *
     * The returned logger logs all messages ([Level.ALL]).
     *
     * Must be closed after use — prefer `.use {}`.
     */
    @MustBeClosed
    fun forFile(file: File): FileLoggerContext {
        val fileHandler = FileHandler(file.absolutePath, 1_000_000, 1, false).apply {
            formatter = PlainTextFormatter.DEFAULT
        }
        val logger = Logger.getAnonymousLogger().apply {
            level = Level.ALL
            useParentHandlers = true
            addHandler(fileHandler)
        }
        return FileLoggerContext(logger, fileHandler)
    }

}
