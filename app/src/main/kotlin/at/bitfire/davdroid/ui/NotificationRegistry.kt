/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import at.bitfire.davdroid.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notifications and channels.
 *
 * As soon as the singleton is created, it will create the necessary notification channels on Android 8+.
 *
 * Don't use the notification IDs for posting notifications directly – always get the instance of this
 * class and use [notifyIfPossible].
 */
@Singleton
class NotificationRegistry @Inject constructor(
    @ApplicationContext val context: Context,
    private val logger: Logger
) {

    companion object {

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

    }


    // notification channel names, accessible only when instance (and thus the channels) has been created

    /**
     * For notifications that don't fit into another channel.
     */
    val CHANNEL_GENERAL = "general"

    /**
     * For debugging notifications. High priority because a debugging session
     * has been activated by the user and they should know all the time.
     *
     * Currently only used for the "verbose logging active" notification.
     */
    val CHANNEL_DEBUG = "debug"

    /**
     * Used to show progress, like that a service detection or WebDAV file access is running.
     */
    val CHANNEL_STATUS = "status"

    /**
     * For sync-related notifications. Use the appropriate sub-channels for different types of sync problems.
     */
    val CHANNEL_SYNC = "sync"

    /**
     * For sync errors that are not IO errors. Shown as normal priority.
     */
    val CHANNEL_SYNC_ERRORS = "syncProblems"

    /**
     * For sync warnings. Shown as low priority.
     */
    val CHANNEL_SYNC_WARNINGS = "syncWarnings"

    /**
     * For sync IO errors. Shown as minimal priority because they might go away automatically, for instance
     * when the connection is working again.
     */
    val CHANNEL_SYNC_IO_ERRORS = "syncIoErrors"


    init {
        createChannels()
    }


    /**
     * Creates notification channels for Android 8+.
     */
    private fun createChannels() {
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

    /**
     * Shows a notification, if possible.
     *
     * If the notification is not possible because the user didn't give notification permissions, it will be ignored.
     *
     * The notification should usually be created using [androidx.core.app.NotificationCompat.Builder].
     *
     * @param id         Notification ID
     * @param tag        Notification tag
     * @param builder    Callback that creates the notification; will only be called if we have the notification permission.
      */
    fun notifyIfPossible(id: Int, tag: String? = null, builder: () -> Notification) {
        if (ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            // we have the permission, show notification
            val nm = NotificationManagerCompat.from(context)
            nm.notify(tag, id, builder())
        } else
            logger.warning("Notifications disabled, not showing notification $id")
    }


    // specific common notifications

    /**
     * Shows a notification about missing permissions.
     *
     * @param intent will be set as content Intent; if null, an Intent to launch PermissionsActivity will be used
     */
    fun notifyPermissions(intent: Intent? = null) {
        notifyIfPossible(NOTIFY_PERMISSIONS) {
            val contentIntent = intent ?: Intent(context, PermissionsActivity::class.java)
            NotificationCompat.Builder(context, CHANNEL_SYNC_ERRORS)
                .setSmallIcon(R.drawable.ic_sync_problem_notify)
                .setContentTitle(context.getString(R.string.sync_error_permissions))
                .setContentText(context.getString(R.string.sync_error_permissions_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        contentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .build()
        }
    }

}