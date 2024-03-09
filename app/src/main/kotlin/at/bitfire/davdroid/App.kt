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
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import at.bitfire.davdroid.log.Logger
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
class App: Application(), Thread.UncaughtExceptionHandler, Configuration.Provider {

    companion object {

        const val HOMEPAGE_PRIVACY = "privacy"

        fun getLauncherBitmap(context: Context) =
                AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()

        /**
         * Gets the DAVx5 Web site URL that should be used to open in the user's browser.
         * Package ID, version number and calling context name will be appended as arguments.
         *
         * @param context   context name to use
         * @param page      optional page segment to append (for instance: [HOMEPAGE_PRIVACY]])
         *
         * @return the Uri for the browser
         */
        fun homepageUrl(context: Context, page: String? = null): Uri {
            val builder = Uri.parse(context.getString(R.string.homepage_url)).buildUpon()

            if (page != null)
                builder.appendPath(page)

            return builder
                .appendQueryParameter("pk_campaign", BuildConfig.APPLICATION_ID)
                .appendQueryParameter("pk_kwd", context::class.java.simpleName)
                .appendQueryParameter("app-version", BuildConfig.VERSION_NAME)
                .build()
        }

    }

    @Inject lateinit var accountsUpdatedListener: AccountsUpdatedListener
    @Inject lateinit var storageLowReceiver: StorageLowReceiver

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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
        UiUtils.updateTheme(this)   // when this is called in the asynchronous thread below, it recreates
                                 // some current activity and causes an IllegalStateException in rare cases

        // don't block UI for some background checks
        thread {
            // watch for account changes/deletions
            accountsUpdatedListener.listen()

            // watch storage because low storage means synchronization is stopped
            storageLowReceiver.listen()

            // watch installed/removed apps
            TasksWatcher.watch(this)
            // check whether a tasks app is currently installed
            SyncUtils.updateTaskSync(this)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this)
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
