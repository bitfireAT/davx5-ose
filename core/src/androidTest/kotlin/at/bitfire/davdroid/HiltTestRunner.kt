/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import at.bitfire.davdroid.di.TestCoroutineDispatchersModule
import at.bitfire.synctools.log.LogcatHandler
import dagger.hilt.android.testing.HiltTestApplication
import java.util.logging.Level
import java.util.logging.Logger

@Suppress("unused")
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader, name: String, context: Context): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        // set root logger to adb Logcat
        val rootLogger = Logger.getLogger("")
        rootLogger.level = Level.ALL
        rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
        rootLogger.addHandler(LogcatHandler(javaClass.name))

        // MockK requirements
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            throw AssertionError("MockK requires Android P [https://mockk.io/ANDROID.html]")

        // set main dispatcher for tests (especially runTest)
        TestCoroutineDispatchersModule.initMainDispatcher()
    }

}