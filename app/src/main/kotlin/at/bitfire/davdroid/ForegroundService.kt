/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.AppSettingsActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()

        /* Call startForeground as soon as possible (must be within 5 seconds after the service has been created).
        If the foreground service shouldn't remain active (because the setting has been disabled),
        we'll immediately stop it with stopForeground() in onStartCommand(). */
        val settingsIntent = Intent(this, AppSettingsActivity::class.java).apply {
            putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, Settings.FOREGROUND_SERVICE)
        }
        val builder = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_foreground_notify)
            .setContentTitle(getString(R.string.foreground_service_notify_title))
            .setContentText(getString(R.string.foreground_service_notify_text))
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        startForeground(NotificationUtils.NOTIFY_FOREGROUND, builder.build())
    }

    companion object {

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface ForegroundServiceEntryPoint {
            fun settingsManager(): SettingsManager
        }

        /**
         * Starts/stops a foreground service, according to the app setting [Settings.FOREGROUND_SERVICE]
         * if [Settings.BATTERY_OPTIMIZATION] is enabled - meaning DAVx5 is whitelisted from optimization.
         */
        const val ACTION_FOREGROUND = "foreground"


        /**
         * Whether the app is currently exempted from battery optimization.
         * @return true if battery optimization is not applied to the current app; false if battery optimization is applied
         */
        private fun batteryOptimizationWhitelisted(context: Context) =
            context.getSystemService<PowerManager>()!!.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)

        /**
         * Whether the foreground service is enabled (checked) in the app settings.
         * @return true: foreground service enabled; false: foreground service not enabled
         */
        fun foregroundServiceActivated(context: Context): Boolean {
            val settingsManager = EntryPointAccessors.fromApplication(context, ForegroundServiceEntryPoint::class.java).settingsManager()
            return settingsManager.getBooleanOrNull(Settings.FOREGROUND_SERVICE) == true
        }

        /**
         * Starts the foreground service when enabled in the app settings and applicable.
         */
        fun startIfActive(context: Context) {
            if (foregroundServiceActivated(context)) {
                if (batteryOptimizationWhitelisted(context)) {
                    val serviceIntent = Intent(ACTION_FOREGROUND, null, context, ForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= 26)
                        // we now have 5 seconds to call Service.startForeground() [https://developer.android.com/about/versions/oreo/android-8.0-changes.html#back-all]
                        context.startForegroundService(serviceIntent)
                    else
                        context.startService(serviceIntent)
                } else
                    notifyBatteryOptimization(context)
            }
        }

        private fun notifyBatteryOptimization(context: Context) {
            val settingsIntent = Intent(context, AppSettingsActivity::class.java).apply {
                putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, Settings.BATTERY_OPTIMIZATION)
            }
            val pendingSettingsIntent = PendingIntent.getActivity(context, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder =
                NotificationCompat.Builder(context, NotificationUtils.CHANNEL_DEBUG)
                    .setSmallIcon(R.drawable.ic_warning_notify)
                    .setContentTitle(context.getString(R.string.battery_optimization_notify_title))
                    .setContentText(context.getString(R.string.battery_optimization_notify_text))
                    .setContentIntent(pendingSettingsIntent)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)

            val nm = NotificationManagerCompat.from(context)
            nm.notifyIfPossible(NotificationUtils.NOTIFY_BATTERY_OPTIMIZATION, builder.build())
        }
    }


    override fun onBind(intent: Intent?): Nothing? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Command is always ACTION_FOREGROUND → re-evaluate foreground setting
        if (foregroundServiceActivated(this))
            // keep service open
            return START_STICKY
        else {
            // don't keep service active
            stopForeground(true)
            stopSelf()      // Stop the service so that onCreate() will run again for the next command
            return START_NOT_STICKY
        }
    }

}