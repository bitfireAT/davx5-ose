/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.App
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.PermissionsActivity
import java.util.*
import java.util.logging.Level

abstract class SyncAdapterService: Service() {

    abstract protected fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!


    abstract class SyncAdapter(
            context: Context
    ): AbstractThreadedSyncAdapter(context, false) {

        abstract fun sync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            Logger.log.log(Level.INFO, "$authority sync of $account has been initiated", extras.keySet().joinToString(", "))

            // required for dav4android (ServiceLoader)
            Thread.currentThread().contextClassLoader = context.classLoader

            sync(account, extras, authority, provider, syncResult)

            Logger.log.info("Sync for $authority complete")
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception when opening content provider for $authority")
            syncResult.databaseError = true

            val intent = Intent(context, PermissionsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val notify = NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_error_light)
                    .setLargeIcon(App.getLauncherBitmap(context))
                    .setContentTitle(context.getString(R.string.sync_error_permissions))
                    .setContentText(context.getString(R.string.sync_error_permissions_text))
                    .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build()
            val nm = NotificationManagerCompat.from(context)
            nm.notify(Constants.NOTIFICATION_PERMISSIONS, notify)
        }

        protected fun checkSyncConditions(settings: AccountSettings): Boolean {
            if (settings.getSyncWifiOnly()) {
                val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetworkInfo
                if (network == null || network.type != ConnectivityManager.TYPE_WIFI || !network.isConnected) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }

                settings.getSyncWifiOnlySSID()?.let { onlySSID ->
                    val quotedSSID = "\"$onlySSID\""
                    val wifi = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val info = wifi.connectionInfo
                    if (info == null || info.ssid != quotedSSID) {
                        Logger.log.info("Connected to wrong WiFi network (${info.ssid}, required: $quotedSSID), ignoring")
                        return false
                    }
                }
            }
            return true
        }

    }

}
