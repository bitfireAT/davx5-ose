/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.os.Build
import android.os.StrictMode
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_FIRST
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Enables StrictMode in debug builds.
 */
class StrictModeSetup @Inject constructor(
    private val logger: Logger
) : StartupAction {

    override val priority = PRIORITY_FIRST

    override fun onAppCreate() {
        if (!BuildConfig.DEBUG)
            return

        logger.info("Debug build, enabling StrictMode with logging")

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        val builder =
            StrictMode.VmPolicy.Builder()     // don't use detectAll() because it causes "untagged socket" warnings
                .detectActivityLeaks()
                .detectFileUriExposure()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.detectContentUriWithoutPermission()
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)   // often triggered by Conscrypt
           builder.detectNonSdkApiUsage()*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            builder.detectUnsafeIntentLaunch()
        StrictMode.setVmPolicy(builder.penaltyLog().build())
    }

}
