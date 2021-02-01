package at.bitfire.davdroid

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.AppSettingsActivity
import at.bitfire.davdroid.ui.NotificationUtils

class ForegroundService : Service() {

    companion object {

        /**
         * Starts/stops a foreground service, according to the app setting [Settings.FOREGROUND_SERVICE].
         */
        const val ACTION_FOREGROUND = "foreground"

    }


    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsManager.getInstance(this)
        val foreground = settings.getBooleanOrNull(Settings.FOREGROUND_SERVICE) == true

        if (foreground) {
            val settingsIntent = Intent(this, AppSettingsActivity::class.java).apply {
                putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, Settings.FOREGROUND_SERVICE)
            }
            val builder = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_STATUS)
                    .setSmallIcon(R.drawable.ic_foreground_notify)
                    .setContentTitle(getString(R.string.foreground_service_notify_title))
                    .setContentText(getString(R.string.foreground_service_notify_text))
                    .setStyle(NotificationCompat.BigTextStyle())
                    .setContentIntent(PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT))
            startForeground(NotificationUtils.NOTIFY_FOREGROUND, builder.build())
            return START_STICKY
        } else {
            stopForeground(true)
            return START_NOT_STICKY
        }
    }

}