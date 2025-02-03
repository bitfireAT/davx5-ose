/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppSettingsActivity
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.io.File
import java.util.Date
import java.util.logging.FileHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Logging handler that logs to a debug log file.
 *
 * Shows a permanent notification as long as it's active (until [close] is called).
 *
 * Only one [LogFileHandler] should be active at once, because the notification is shared.
 */
class LogFileHandler @Inject constructor(
    @ApplicationContext val context: Context,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry
): Handler(), Closeable {

    companion object {

        private const val DEBUG_INFO_DIRECTORY = "debug"

        /**
         * Creates (when necessary) and returns the directory where all the debug files (such as log files) are stored.
         * Must match the contents of `res/xml/debug.paths.xml`.
         *
         * @return The directory where all debug info are stored, or `null` if the directory couldn't be created successfully.
         */
        fun debugDir(context: Context): File? {
            val dir = File(context.filesDir, DEBUG_INFO_DIRECTORY)
            if (dir.exists() && dir.isDirectory)
                return dir

            if (dir.mkdir())
                return dir

            return null
        }

        /**
         * The file (in [debugDir]) where verbose logs are stored.
         *
         * @return The file where verbose logs are stored, or `null` if there's no [debugDir].
         */
        fun getDebugLogFile(context: Context): File? {
            val logDir = debugDir(context) ?: return null
            return File(logDir, "davx5-log.txt")
        }

    }

    private var fileHandler: FileHandler? = null
    private val notificationManager = NotificationManagerCompat.from(context)

    private val logFile = getDebugLogFile(context)

    init {
        if (logFile != null) {
            if (logFile.createNewFile())
                logFile.writeText("Log file created at ${Date()}; PID ${Process.myPid()}; UID ${Process.myUid()}\n")

            // actual logging is handled by a FileHandler
            fileHandler = FileHandler(logFile.toString(), true).apply {
                formatter = PlainTextFormatter.DEFAULT
            }

            showNotification()
        } else {
            logger.severe("Couldn't create log file in app-private directory $DEBUG_INFO_DIRECTORY/.")
            level = Level.OFF
        }
    }


    @Synchronized
    override fun publish(record: LogRecord) {
        fileHandler?.publish(record)
    }

    @Synchronized
    override fun flush() {
        fileHandler?.flush()
    }

    @Synchronized
    override fun close() {
        fileHandler?.close()
        fileHandler = null

        // remove all files in debug info directory, may also contain zip files from debug info activity etc.
        logFile?.parentFile?.deleteRecursively()

        removeNotification()
    }


    // notifications

    private fun showNotification() {
        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_VERBOSE_LOGGING) {
            val builder = NotificationCompat.Builder(context, notificationRegistry.CHANNEL_DEBUG)
            builder.setSmallIcon(R.drawable.ic_sd_card_notify)
                .setContentTitle(context.getString(R.string.app_settings_logging))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentText(
                    context.getString(
                        R.string.logging_notification_text, context.getString(
                            R.string.app_name
                        )
                    )
                )
                .setOngoing(true)

            // add action to view/share the logs
            val shareIntent = DebugInfoActivity.IntentBuilder(context)
                .newTask()
                .share()
            val pendingShare = TaskStackBuilder.create(context)
                .addNextIntent(shareIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_share,
                    context.getString(R.string.logging_notification_view_share),
                    pendingShare
                ).build()
            )

            // add action to disable verbose logging
            val prefIntent = Intent(context, AppSettingsActivity::class.java)
            prefIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingPref = TaskStackBuilder.create(context)
                .addNextIntent(prefIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_settings,
                    context.getString(R.string.logging_notification_disable),
                    pendingPref
                ).build()
            )

            builder.build()
        }
   }

    private fun removeNotification() {
        notificationManager.cancel(NotificationRegistry.NOTIFY_VERBOSE_LOGGING)
    }

}