/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.log

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppSettingsActivity
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import java.io.File
import java.io.IOException
import java.util.*
import java.util.logging.FileHandler
import java.util.logging.Level

object Logger : SharedPreferences.OnSharedPreferenceChangeListener {

    const val LOGGER_NAME = "davx5"
    private const val LOG_TO_FILE = "log_to_file"

    val log: java.util.logging.Logger = java.util.logging.Logger.getLogger(LOGGER_NAME)

    private lateinit var context: Application
    private lateinit var preferences: SharedPreferences


    fun initialize(app: Application) {
        context = app
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

    @Synchronized
    private fun reinitialize() {
        val logToFile = preferences.getBoolean(LOG_TO_FILE, false)
        val logVerbose = logToFile || BuildConfig.DEBUG || Log.isLoggable(log.name, Log.DEBUG)

        log.info("Verbose logging: $logVerbose; to file: $logToFile")

        // set logging level according to preferences
        val rootLogger = java.util.logging.Logger.getLogger("")
        rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

        // reset all handlers and add our own logcat handler
        rootLogger.useParentHandlers = false
        rootLogger.handlers.forEach { handler ->
            rootLogger.removeHandler(handler)
            if (handler is FileHandler)     // gracefully close previous verbose-logging FileHandlers
                handler.close()
        }
        rootLogger.addHandler(LogcatHandler)

        val nm = NotificationManagerCompat.from(context)
        // log to external file according to preferences
        if (logToFile) {
            val logDir = debugDir() ?: return
            val logFile = File(logDir, "davx5-log.txt")
            if (logFile.createNewFile())
                logFile.writeText("Log file created at ${Date()}; PID ${Process.myPid()}; UID ${Process.myUid()}\n")

            try {
                val fileHandler = FileHandler(logFile.toString(), true).apply {
                    formatter = PlainTextFormatter.DEFAULT
                }
                rootLogger.addHandler(fileHandler)
                log.info("Now logging to file: $logFile")

                val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_DEBUG)
                builder .setSmallIcon(R.drawable.ic_sd_card_notify)
                        .setContentTitle(context.getString(R.string.app_settings_logging))
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentText(context.getString(R.string.logging_notification_text, context.getString(R.string.app_name)))
                        .setOngoing(true)

                val shareIntent = DebugInfoActivity.IntentBuilder(context)
                    .withLogFile(logFile)
                    .newTask()
                    .share()
                val pendingShare = PendingIntent.getActivity(context, 0, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(NotificationCompat.Action.Builder(
                        R.drawable.ic_share,
                        context.getString(R.string.logging_notification_view_share),
                        pendingShare
                ).build())

                val prefIntent = Intent(context, AppSettingsActivity::class.java)
                prefIntent.putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, LOG_TO_FILE)
                prefIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pendingPref = PendingIntent.getActivity(context, 0, prefIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(NotificationCompat.Action.Builder(
                        R.drawable.ic_settings,
                        context.getString(R.string.logging_notification_disable),
                        pendingPref
                ).build())

                nm.notify(NotificationUtils.NOTIFY_VERBOSE_LOGGING, builder.build())
            } catch(e: IOException) {
                log.log(Level.SEVERE, "Couldn't create log file", e)
                Toast.makeText(context, context.getString(R.string.logging_couldnt_create_file), Toast.LENGTH_LONG).show()
            }
        } else {
            // verbose logging is disabled -> cancel notification and remove old logs
            nm.cancel(NotificationUtils.NOTIFY_VERBOSE_LOGGING)
            debugDir()?.deleteRecursively()
        }
    }


    private fun debugDir(): File? {
        val dir = File(context.filesDir, "debug")
        if (dir.exists() && dir.isDirectory)
            return dir

        if (dir.mkdir())
            return dir

        Toast.makeText(context, context.getString(R.string.logging_couldnt_create_file), Toast.LENGTH_LONG).show()
        return null
    }

}