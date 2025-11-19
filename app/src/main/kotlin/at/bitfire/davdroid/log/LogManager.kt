/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import android.content.Context
import android.util.Log
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.synctools.log.LogcatHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Handles logging configuration and which loggers are active at a moment.
 * To initialize, just make sure that the [LogManager] singleton is created.
 *
 * Configures the root logger like this:
 *
 * - Always logs to logcat.
 * - Watches the "log to file" preference and activates or deactivates file logging accordingly.
 * - If "log to file" is enabled, log level is set to [Level.ALL].
 * - Otherwise, log level is set to [Level.INFO].
 *
 * Preferred ways to get a [Logger] are:
 *
 * - `@Inject` [Logger] for a general-purpose logger when injection is possible
 * - `Logger.getGlobal()` for a general-purpose logger
 * - `Logger.getLogger(javaClass.name)` for a specific logger that can be customized
 *
 * When using the global logger, the class name of the logging calls will still be logged, so there's
 * no need to always get a separate logger for each class (only if the class wants to customize it).
 *
 * Note about choosing log levels: records with [Level.FINE] or higher will always be printed to adb logs
 * (regardless of whether verbose logging is active). Records with a lower level will only
 * by printed to adb logs when verbose logging is active.
 */
@Singleton
class LogManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logFileHandler: Provider<LogFileHandler>,
    private val logger: Logger,
    private val prefs: PreferenceRepository
) : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        // observe preference changes
        scope.launch {
            prefs.logToFileFlow().collect {
                reloadConfig()
            }
        }

        reloadConfig()
    }

    override fun close() {
        scope.cancel()
    }

    @Synchronized
    fun reloadConfig() {
        val logToFile = prefs.logToFile()
        val logVerbose = logToFile || BuildConfig.DEBUG || Log.isLoggable(logger.name, Log.DEBUG)
        logger.info("Verbose logging = $logVerbose; log to file = $logToFile")

        // reset existing loggers and initialize from assets/logging.properties
        context.assets.open("logging.properties").use {
            val javaLogManager = java.util.logging.LogManager.getLogManager()
            javaLogManager.readConfiguration(it)
        }

        // root logger: set default log level and always log to logcat
        val rootLogger = Logger.getLogger("")
        rootLogger.level = if (logVerbose)
            Level.ALL       // verbose logs, include everything (including HTTP interceptor logs)
        else
            Level.FINE      // include more detailed information like content provider operations
        rootLogger.addHandler(LogcatHandler(BuildConfig.APPLICATION_ID))

        // log to file, if requested
        if (logToFile)
            rootLogger.addHandler(logFileHandler.get())
    }

}