/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.davdroid.log.LogcatHandler
import at.bitfire.davdroid.log.PlainTextFormatter
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.Settings
import okhttp3.internal.tls.OkHostnameVerifier
import org.apache.commons.lang3.time.DateFormatUtils
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.HostnameVerifier

class App: Application() {

    companion object {

        @JvmField val DISTRUST_SYSTEM_CERTIFICATES = "distrustSystemCerts"
        @JvmField val LOG_TO_EXTERNAL_STORAGE = "logToExternalStorage"
        @JvmField val OVERRIDE_PROXY = "overrideProxy"
        @JvmField val OVERRIDE_PROXY_HOST = "overrideProxyHost"
        @JvmField val OVERRIDE_PROXY_PORT = "overrideProxyPort"

        @JvmField val OVERRIDE_PROXY_HOST_DEFAULT = "localhost"
        @JvmField val OVERRIDE_PROXY_PORT_DEFAULT = 8118

        @JvmField
        val log = Logger.getLogger("davdroid")!!
        init {
            at.bitfire.dav4android.Constants.log = Logger.getLogger("davdroid.dav4android")
            at.bitfire.cert4android.Constants.log = Logger.getLogger("davdroid.cert4android")
        }

        lateinit var addressBookAccountType: String
        lateinit var addressBooksAuthority: String

        @JvmStatic
        fun getLauncherBitmap(context: Context): Bitmap? {
            val drawableLogo = if (android.os.Build.VERSION.SDK_INT >= 21)
                    context.getDrawable(R.mipmap.ic_launcher)
                else
                    @Suppress("deprecation")
                    context.resources.getDrawable(R.mipmap.ic_launcher)
            return if (drawableLogo is BitmapDrawable)
                drawableLogo.bitmap
            else
                null
        }

    }

    var certManager: CustomCertManager? = null
    var sslSocketFactoryCompat: SSLSocketFactoryCompat? = null
    var hostnameVerifier: HostnameVerifier? = null


    override fun onCreate() {
        super.onCreate()

        reinitCertManager()
        reinitLogger()

        addressBookAccountType = getString(R.string.account_type_address_book)
        addressBooksAuthority = getString(R.string.address_books_authority)
    }

    fun reinitCertManager() {
        if (true /* custom certificate support */) {
            certManager?.close()

            ServiceDB.OpenHelper(this).use { dbHelper ->
                val settings = Settings(dbHelper.readableDatabase)
                val mgr = CustomCertManager(this, !settings.getBoolean(DISTRUST_SYSTEM_CERTIFICATES, false))
                sslSocketFactoryCompat = SSLSocketFactoryCompat(mgr)
                hostnameVerifier = mgr.hostnameVerifier(OkHostnameVerifier.INSTANCE)

                certManager = mgr
            }
        }
    }

    fun reinitLogger() {
        ServiceDB.OpenHelper(this).use { dbHelper ->
            val settings = Settings(dbHelper.getReadableDatabase())

            val logToFile = settings.getBoolean(LOG_TO_EXTERNAL_STORAGE, false)
            val logVerbose = logToFile || Log.isLoggable(log.name, Log.DEBUG)

            App.log.info("Verbose logging: $logVerbose")

            // set logging level according to preferences
            val rootLogger = Logger.getLogger("")
            rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

            // remove all handlers and add our own logcat handler
            rootLogger.useParentHandlers = false
            rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
            rootLogger.addHandler(LogcatHandler)

            val nm = NotificationManagerCompat.from(this)
            // log to external file according to preferences
            if (logToFile) {
                val builder = NotificationCompat.Builder(this)
                builder .setSmallIcon(R.drawable.ic_sd_storage_light)
                        .setLargeIcon(getLauncherBitmap(this))
                        .setContentTitle(getString(R.string.logging_davdroid_file_logging))
                        .setLocalOnly(true)

                val dir = getExternalFilesDir(null)
                if (dir != null)
                    try {
                        val fileName = File(dir, "davdroid-${Process.myPid()}-${DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss")}.txt").toString()
                        log.info("Logging to $fileName")

                        val fileHandler = FileHandler(fileName)
                        fileHandler.formatter = PlainTextFormatter.DEFAULT
                        log.addHandler(fileHandler)
                        builder .setContentText(dir.path)
                                .setSubText(getString(R.string.logging_to_external_storage_warning))
                                .setCategory(NotificationCompat.CATEGORY_STATUS)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setStyle(NotificationCompat.BigTextStyle()
                                        .bigText(getString(R.string.logging_to_external_storage, dir.path)))
                                .setOngoing(true)

                    } catch (e: IOException) {
                        log.log(Level.SEVERE, "Couldn't create external log file", e)

                        builder .setContentText(getString(R.string.logging_couldnt_create_file, e.localizedMessage))
                                .setCategory(NotificationCompat.CATEGORY_ERROR)
                    }
                else
                    builder.setContentText(getString(R.string.logging_no_external_storage))

                nm.notify(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING, builder.build())
            } else
                nm.cancel(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING)
        }
    }


    class ReinitSettingsReceiver: BroadcastReceiver() {

        companion object {
            @JvmField val ACTION_REINIT_SETTINGS = "at.bitfire.davdroid.REINIT_SETTINGS"
        }

        override fun onReceive(context: Context, intent: Intent) {
            log.info("Received broadcast: re-initializing settings (logger/cert manager)")

            val app = context.applicationContext
            if (app is App) {
                app.reinitLogger()
                app.reinitCertManager()
            } else
                App.log.severe("context is ${app::class.java.canonicalName} instead of App")
        }

    }
}
