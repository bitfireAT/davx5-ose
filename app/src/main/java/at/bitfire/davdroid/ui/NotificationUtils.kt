/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R

object NotificationUtils {

    // notification IDs
    const val NOTIFY_EXTERNAL_FILE_LOGGING = 1
    const val NOTIFY_REFRESH_COLLECTIONS = 2
    const val NOTIFY_SYNC_ERROR = 10
    const val NOTIFY_INVALID_RESOURCE = 11
    const val NOTIFY_OPENTASKS = 20
    const val NOTIFY_PERMISSIONS = 21
    const val NOTIFY_LICENSE = 100

    // notification channels
    const val CHANNEL_GENERAL = "general"
    const val CHANNEL_DEBUG = "debug"

    private const val CHANNEL_SYNC = "sync"
    const val CHANNEL_SYNC_ERRORS = "syncProblems"
    const val CHANNEL_SYNC_WARNINGS = "syncWarnings"
    const val CHANNEL_SYNC_IO_ERRORS = "syncIoErrors"


    fun createChannels(context: Context) {
        @TargetApi(Build.VERSION_CODES.O)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_SYNC, context.getString(R.string.notification_channel_sync)))

            nm.createNotificationChannels(listOf(
                    NotificationChannel(CHANNEL_DEBUG, context.getString(R.string.notification_channel_debugging), NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CHANNEL_GENERAL, context.getString(R.string.notification_channel_general), NotificationManager.IMPORTANCE_DEFAULT),

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

    fun newBuilder(context: Context, channel: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel)
                .setColor(context.resources.getColor(R.color.primaryColor))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            builder.setLargeIcon(App.getLauncherBitmap(context))

        return builder
    }

}