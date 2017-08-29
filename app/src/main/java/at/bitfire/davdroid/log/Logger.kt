/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import at.bitfire.davdroid.App
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.Settings
import at.bitfire.davdroid.ui.AppSettingsActivity
import org.apache.commons.lang3.time.DateFormatUtils
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger

object Logger {

    @JvmField
    val log = Logger.getLogger("davdroid")!!


    fun reinitLogger(context: Context) {
        ServiceDB.OpenHelper(context).use { dbHelper ->
            val settings = Settings(dbHelper.readableDatabase)

            val logToFile = settings.getBoolean(App.LOG_TO_EXTERNAL_STORAGE, false)
            val logVerbose = logToFile || Log.isLoggable(log.name, Log.DEBUG)

            log.info("Verbose logging: $logVerbose")

            // set logging level according to preferences
            val rootLogger = Logger.getLogger("")
            rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

            // remove all handlers and add our own logcat handler
            rootLogger.useParentHandlers = false
            rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
            rootLogger.addHandler(LogcatHandler)

            val nm = NotificationManagerCompat.from(context)
            // log to external file according to preferences
            if (logToFile) {
                val builder = NotificationCompat.Builder(context)
                builder .setSmallIcon(R.drawable.ic_sd_storage_light)
                        .setLargeIcon(App.getLauncherBitmap(context))
                        .setContentTitle(context.getString(R.string.logging_davdroid_file_logging))
                        .setLocalOnly(true)

                val dir = context.getExternalFilesDir(null)
                if (dir != null)
                    try {
                        val fileName = File(dir, "davdroid-${Process.myPid()}-${DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss")}.txt").toString()
                        log.info("Logging to $fileName")

                        val fileHandler = FileHandler(fileName)
                        fileHandler.formatter = PlainTextFormatter.DEFAULT
                        rootLogger.addHandler(fileHandler)

                        val prefIntent = Intent(context, AppSettingsActivity::class.java)
                        prefIntent.putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, "log_to_external_storage")

                        builder .setContentText(dir.path)
                                .setSubText(context.getString(R.string.logging_to_external_storage_warning))
                                .setCategory(NotificationCompat.CATEGORY_STATUS)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setContentIntent(PendingIntent.getActivity(context, 0, prefIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                                .setStyle(NotificationCompat.BigTextStyle()
                                        .bigText(context.getString(R.string.logging_to_external_storage, dir.path)))
                                .setOngoing(true)

                    } catch(e: IOException) {
                        log.log(Level.SEVERE, "Couldn't create external log file", e)

                        builder .setContentText(context.getString(R.string.logging_couldnt_create_file, e.localizedMessage))
                                .setCategory(NotificationCompat.CATEGORY_ERROR)
                    }
                else
                    builder.setContentText(context.getString(R.string.logging_no_external_storage))

                nm.notify(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING, builder.build())
            } else
                nm.cancel(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING)
        }
    }

}