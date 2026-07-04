/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import at.bitfire.synctools.log.LogcatHandler
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Custom test runner that enables verbose logging to Android logcat during tests.
 */
@Suppress("unused")
class LoggingTestRunner: AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        // enable verbose logging during tests
        val rootLogger = Logger.getLogger("")

        // verbose logging
        rootLogger.level = Level.ALL

        // log to logcat
        rootLogger.addHandler(LogcatHandler())
    }

}