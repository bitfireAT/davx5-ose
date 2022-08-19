/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.StrictMode
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.AccountsUpdatedListener
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.UiUtils
import dagger.hilt.android.HiltAndroidApp
import java.util.logging.Level
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@HiltAndroidApp
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

    @Inject lateinit var accountsUpdatedListener: AccountsUpdatedListener
    @Inject lateinit var storageLowReceiver: StorageLowReceiver


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

        // set light/dark mode
        UiUtils.setTheme(this)   // when this is called in the asynchronous thread below, it recreates
                                 // some current activity and causes an IllegalStateException in rare cases

        // don't block UI for some background checks
        thread {
            // watch for account changes/deletions
            accountsUpdatedListener.listen()

            // foreground service (possible workaround for devices which prevent DAVx5 from being started)
            ForegroundService.startIfActive(this)

            // watch storage because low storage means synchronization is stopped
            storageLowReceiver.listen()

            // watch installed/removed apps
            TasksWatcher.watch(this)
            // check whether a tasks app is currently installed
            SyncUtils.updateTaskSync(this)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this)

            // check/repair sync intervals
            AccountSettings.repairSyncIntervals(this)
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        Logger.log.log(Level.SEVERE, "Unhandled exception!", e)

        val intent = DebugInfoActivity.IntentBuilder(this)
            .withCause(e)
            .newTask()
            .build()
        startActivity(intent)

        exitProcess(1)
    }

}
