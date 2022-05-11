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
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.AccountsUpdatedListener
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.UiUtils
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import java.util.logging.Level
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@Suppress("unused")
class App: Application(), KoinComponent, Thread.UncaughtExceptionHandler {

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


    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        startKoin {
            androidLogger(
                if (BuildConfig.DEBUG)
                    org.koin.core.logger.Level.DEBUG
                else
                    org.koin.core.logger.Level.INFO
            )
            androidContext(base)

            modules(
                AccountsUpdatedListener.defaultModule,
                AppDatabase.defaultModule,
                SettingsManager.defaultModule,
                StorageLowReceiver.defaultModule
            )
        }
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

        // set light/dark mode
        UiUtils.setTheme()      // when this is called in the asynchronous thread below, it recreates
                                // some current activity and causes an IllegalStateException in rare cases

        // don't block UI for some background checks
        thread {
            // watch for account changes/deletions
            get<AccountsUpdatedListener>().listen()

            // foreground service (possible workaround for devices which prevent DAVx5 from being started)
            ForegroundService.startIfActive(this)

            // watch storage because low storage means synchronization is stopped
            get<StorageLowReceiver>().listen()

            // watch installed/removed apps
            TasksWatcher.watch(this)
            // check whether a tasks app is currently installed
            TasksWatcher.updateTaskSync(this)

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
