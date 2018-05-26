/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.Manifest
import android.accounts.Account
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.AccountActivity
import at.bitfire.davdroid.ui.AccountSettingsActivity
import at.bitfire.davdroid.ui.NotificationUtils
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level

abstract class SyncAdapterService: Service() {

    companion object {
        /** Keep a list of running syncs to block multiple calls at the same time,
         *  like run by some devices. Weak references are used for the case that a thread
         *  is terminated and the `finally` block which cleans up [runningSyncs] is not
         *  executed. */
        private val runningSyncs = mutableListOf<WeakReference<Pair<String, Account>>>()
    }

    protected abstract fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!


    abstract class SyncAdapter(
            context: Context
    ): AbstractThreadedSyncAdapter(context, false) {

        abstract fun sync(settings: ISettings, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            Logger.log.log(Level.INFO, "$authority sync of $account has been initiated", extras.keySet().joinToString(", "))

            // prevent multiple syncs of the same authority to be run for the same account
            val currentSync = Pair(authority, account)
            synchronized(runningSyncs) {
                if (runningSyncs.any { it.get() == currentSync }) {
                    Logger.log.warning("There's already another $authority sync running for $account, aborting")
                    return
                }
                runningSyncs += WeakReference(currentSync)
            }

            try {
                // required for dav4android (ServiceLoader)
                Thread.currentThread().contextClassLoader = context.classLoader

                // load app settings
                Settings.getInstance(context).use { settings ->
                    if (settings == null) {
                        syncResult.databaseError = true
                        Logger.log.severe("Couldn't connect to Settings service, aborting sync")
                        return
                    }

                    //if (runSync) {
                        SyncManager.cancelNotifications(NotificationManagerCompat.from(context), authority, account)
                        sync(settings, account, extras, authority, provider, syncResult)
                    //}
                }
            } finally {
                synchronized(runningSyncs) {
                    runningSyncs.removeAll { it.get() == null || it.get() == currentSync }
                }
            }

            Logger.log.info("Sync for $currentSync finished")
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception when opening content provider for $authority")
            syncResult.databaseError = true

            val intent = Intent(context, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            notifyPermissions(intent)
        }

        protected fun checkSyncConditions(settings: AccountSettings): Boolean {
            if (settings.getSyncWifiOnly()) {
                val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetworkInfo
                if (network == null || network.type != ConnectivityManager.TYPE_WIFI || !network.isConnected) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }

                settings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
                    // getting the WiFi name requires location permission (and active location services) since Android 8.1
                    // see https://issuetracker.google.com/issues/70633700
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(context, AccountSettingsActivity::class.java)
                        intent.putExtra(AccountActivity.EXTRA_ACCOUNT, settings.account)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        notifyPermissions(intent)
                    }

                    val wifi = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val info = wifi.connectionInfo
                    if (info == null || !onlySSIDs.contains(info.ssid.trim('"'))) {
                        Logger.log.info("Connected to wrong WiFi network (${info.ssid}), ignoring")
                        return false
                    }
                }
            }
            return true
        }

        private fun notifyPermissions(intent: Intent) {
            val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
                    .setSmallIcon(R.drawable.ic_sync_error_notification)
                    .setContentTitle(context.getString(R.string.sync_error_permissions))
                    .setContentText(context.getString(R.string.sync_error_permissions_text))
                    .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(NotificationUtils.NOTIFY_PERMISSIONS, notify)
        }

    }

}
