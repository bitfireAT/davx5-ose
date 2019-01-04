/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppSettingsActivity
import at.bitfire.davdroid.ui.NotificationUtils
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Level

@SuppressLint("StaticFieldLeak")    // we'll only keep an app context
object Logger : SharedPreferences.OnSharedPreferenceChangeListener {

    private const val LOG_TO_FILE = "log_to_file"

    val log = java.util.logging.Logger.getLogger("davdroid")!!

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    fun initialize(someContext: Context) {
        context = someContext.applicationContext
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)

        reinitialize()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == LOG_TO_FILE) {
            log.info("Logging settings changed; re-initializing logger")
            reinitialize()
        }
    }

    private fun reinitialize() {
        val logToFile = preferences.getBoolean(LOG_TO_FILE, false)
        val logVerbose = logToFile || Log.isLoggable(log.name, Log.DEBUG)

        log.info("Verbose logging: $logVerbose; to file: $logToFile")

        // set logging level according to preferences
        val rootLogger = java.util.logging.Logger.getLogger("")
        rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

        // remove all handlers and add our own logcat handler
        rootLogger.useParentHandlers = false
        rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
        rootLogger.addHandler(LogcatHandler)

        val nm = NotificationManagerCompat.from(context)
        // log to external file according to preferences
        if (logToFile) {
            val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_DEBUG)
            builder .setSmallIcon(R.drawable.ic_sd_storage_notification)
                    .setContentTitle(context.getString(R.string.logging_notification_title))

            val logDir = debugDir(context) ?: return
            val logFile = File(logDir, "davx5-log.txt")

            try {
                val fileHandler = FileHandler(logFile.toString(), true)
                fileHandler.formatter = PlainTextFormatter.DEFAULT
                rootLogger.addHandler(fileHandler)

                val prefIntent = Intent(context, AppSettingsActivity::class.java)
                prefIntent.putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, LOG_TO_FILE)

                builder .setContentText(logDir.path)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentText(context.getString(R.string.logging_notification_text))
                        .setContentIntent(PendingIntent.getActivity(context, 0, prefIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .setOngoing(true)

                // add "Share" action
                val logFileUri = FileProvider.getUriForFile(context, context.getString(R.string.authority_debug_provider), logFile)
                log.fine("Now logging to file: $logFile -> $logFileUri")

                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "DAVx⁵ logs")
                shareIntent.putExtra(Intent.EXTRA_STREAM, logFileUri)
                shareIntent.type = "text/plain"
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val shareAction = NotificationCompat.Action.Builder(R.drawable.ic_share_action,
                        context.getString(R.string.logging_notification_share_log),
                        PendingIntent.getActivity(context, 0, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                builder.addAction(shareAction.build())
            } catch(e: IOException) {
                log.log(Level.SEVERE, "Couldn't create log file", e)
                Toast.makeText(context, context.getString(R.string.logging_couldnt_create_file), Toast.LENGTH_LONG).show()
            }

            nm.notify(NotificationUtils.NOTIFY_EXTERNAL_FILE_LOGGING, builder.build())
        } else {
            nm.cancel(NotificationUtils.NOTIFY_EXTERNAL_FILE_LOGGING)

            // delete old logs
            debugDir(context)?.deleteRecursively()
        }
    }


    private fun debugDir(context: Context): File? {
        val dir = File(context.filesDir, "debug")
        if (dir.exists() && dir.isDirectory)
            return dir

        if (dir.mkdir())
            return dir

        Toast.makeText(context, context.getString(R.string.logging_couldnt_create_file), Toast.LENGTH_LONG).show()
        return null
    }

}