/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.util.logging.LogManager

/**
 * Custom test runner that enables verbose logging to Android logcat during tests.
 */
@Suppress("unused")
class LoggingTestRunner: AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        // reset existing loggers and initialize from assets/logging.properties
        context.assets.open("logging.properties").use {
            val javaLogManager = LogManager.getLogManager()
            javaLogManager.readConfiguration(it)
        }
    }

}