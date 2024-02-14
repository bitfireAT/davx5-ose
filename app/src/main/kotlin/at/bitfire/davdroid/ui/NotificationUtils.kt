/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

object NotificationUtils {

    // notification IDs
    const val NOTIFY_VERBOSE_LOGGING = 1
    const val NOTIFY_REFRESH_COLLECTIONS = 2
    const val NOTIFY_DATABASE_CORRUPTED = 4
    const val NOTIFY_SYNC_ERROR = 10
    const val NOTIFY_INVALID_RESOURCE = 11
    const val NOTIFY_WEBDAV_ACCESS = 12
    const val NOTIFY_SYNC_EXPEDITED = 14
    const val NOTIFY_TASKS_PROVIDER_TOO_OLD = 20
    const val NOTIFY_PERMISSIONS = 21

    const val NOTIFY_LICENSE = 100

    // notification channels
    const val CHANNEL_GENERAL = "general"
    const val CHANNEL_DEBUG = "debug"
    const val CHANNEL_STATUS = "status"

    private const val CHANNEL_SYNC = "sync"
    const val CHANNEL_SYNC_ERRORS = "syncProblems"
    const val CHANNEL_SYNC_WARNINGS = "syncWarnings"
    const val CHANNEL_SYNC_IO_ERRORS = "syncIoErrors"


    fun createChannels(context: Context) {
        @TargetApi(Build.VERSION_CODES.O)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService<NotificationManager>()!!

            nm.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_SYNC, context.getString(R.string.notification_channel_sync)))

            nm.createNotificationChannels(listOf(
                    NotificationChannel(CHANNEL_DEBUG, context.getString(R.string.notification_channel_debugging), NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CHANNEL_GENERAL, context.getString(R.string.notification_channel_general), NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(CHANNEL_STATUS, context.getString(R.string.notification_channel_status), NotificationManager.IMPORTANCE_LOW),

                    NotificationChannel(CHANNEL_SYNC_ERRORS, context.getString(R.string.notification_channel_sync_errors), NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = context.getString(R.string.notification_channel_sync_errors_desc)
                        group = CHANNEL_SYNC
                    },
                    NotificationChannel(CHANNEL_SYNC_WARNINGS, context.getString(R.string.notification_channel_sync_warnings), NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(R.string.notification_channel_sync_warnings_desc)
                        group = CHANNEL_SYNC
                    },
                    NotificationChannel(CHANNEL_SYNC_IO_ERRORS, context.getString(R.string.notification_channel_sync_io_errors), NotificationManager.IMPORTANCE_MIN).apply {
                        description = context.getString(R.string.notification_channel_sync_io_errors_desc)
                        group = CHANNEL_SYNC
                    }
            ))
        }
    }

    fun newBuilder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setColor(ResourcesCompat.getColor(context.resources, R.color.primaryColor, null))


    fun NotificationManagerCompat.notifyIfPossible(tag: String?, id: Int, notification: Notification) {
        try {
            notify(tag, id, notification)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Couldn't post notification (SecurityException)", notification)
        }
    }

    fun NotificationManagerCompat.notifyIfPossible(id: Int, notification: Notification) =
        notifyIfPossible(null, id, notification)

}