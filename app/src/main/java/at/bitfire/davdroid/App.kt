/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.UiUtils
import java.util.logging.Level
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@Suppress("unused")
class App: Application(), Thread.UncaughtExceptionHandler {

    companion object {

        fun getLauncherBitmap(context: Context) =
                AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()

        fun homepageUrl(context: Context) =
                Uri.parse(context.getString(R.string.homepage_url)).buildUpon()
                        .appendQueryParameter("pk_campaign", BuildConfig.APPLICATION_ID)
                        .appendQueryParameter("pk_kwd", context::class.java.simpleName)
                        .appendQueryParameter("app-version", BuildConfig.VERSION_NAME)
                        .build()!!

    }


    override fun onCreate() {
        super.onCreate()
        Logger.initialize(this)

        if (BuildConfig.DEBUG)
            // debug builds
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectFileUriExposure()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build())
        else // if (BuildConfig.FLAVOR == FLAVOR_STANDARD)
            // handle uncaught exceptions in non-debug standard flavor
            Thread.setDefaultUncaughtExceptionHandler(this)

        NotificationUtils.createChannels(this)

        // don't block UI for some background checks
        thread {
            // set light/dark mode
            UiUtils.setTheme(this)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this)

            // watch installed/removed apps
            TasksWatcher(this)

            // check whether a tasks app is currently installed
            TasksWatcher.updateTaskSync(this)

            // check/repair sync intervals
            AccountSettings.repairSyncIntervals(this)

            // foreground service (possible workaround for devices which prevent DAVx5 from being started)
            ForegroundService.startIfEnabled(this)
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        Logger.log.log(Level.SEVERE, "Unhandled exception!", e)

        val intent = Intent(this, DebugInfoActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(DebugInfoActivity.EXTRA_CAUSE, e)
        startActivity(intent)

        exitProcess(1)
    }

}
