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
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.account.AccountActivity
import at.bitfire.davdroid.ui.account.SettingsActivity
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

        /**
         * Specifies an list of IDs which are requested to be synchronized before
         * the other collections. For instance, if some calendars of a CalDAV
         * account are visible in the calendar app and others are hidden, the visible calendars can
         * be synchronized first, so that the "Refresh" action in the calendar app is more responsive.
         *
         * Extra type: String (comma-separated list of IDs)
         *
         * In case of calendar sync, the extra value is a list of Android calendar IDs.
         * In case of task sync, the extra value is an a list of OpenTask task list IDs.
         */
        const val SYNC_EXTRAS_PRIORITY_COLLECTIONS = "priority_collections"

        /**
         * Requests a re-synchronization of all entries. For instance, if this extra is
         * set for a calendar sync, all remote events will be listed and checked for remote
         * changes again.
         *
         * Useful if settings which modify the remote resource list (like the CalDAV setting
         * "sync events n days in the past") have been changed.
         */
        const val SYNC_EXTRAS_RESYNC = "resync"

        /**
         * Requests a full re-synchronization of all entries. For instance, if this extra is
         * set for an address book sync, all contacts will be downloaded again and updated in the
         * local storage.
         *
         * Useful if settings which modify parsing/local behavior have been changed.
         */
        const val SYNC_EXTRAS_FULL_RESYNC = "full_resync"
    }

    protected abstract fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!


    abstract class SyncAdapter(
            context: Context
    ): AbstractThreadedSyncAdapter(context, false) {

        companion object {
            fun priorityCollections(extras: Bundle): Set<Long> {
                val ids = mutableSetOf<Long>()
                extras.getString(SYNC_EXTRAS_PRIORITY_COLLECTIONS)?.let { rawIds ->
                    for (rawId in rawIds.split(','))
                        try {
                            ids += rawId.toLong()
                        } catch (e: NumberFormatException) {
                            Logger.log.log(Level.WARNING, "Couldn't parse SYNC_EXTRAS_PRIORITY_COLLECTIONS", e)
                        }
                }
                return ids
            }
        }


        abstract fun sync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult)

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
                // required for dav4jvm (ServiceLoader)
                Thread.currentThread().contextClassLoader = context.classLoader

                sync(account, extras, authority, provider, syncResult)
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
                // WiFi required
                val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

                // check for connected WiFi network
                var wifiAvailable = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    connectivityManager.allNetworks.forEach { network ->
                        connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                                wifiAvailable = true
                        }
                    }
                } else {
                    val network = connectivityManager.activeNetworkInfo
                    if (network?.isConnected == true && network.type == ConnectivityManager.TYPE_WIFI)
                        wifiAvailable = true
                }
                if (!wifiAvailable) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }
                // if execution reaches this point, we're on a connected WiFi

                settings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
                    // getting the WiFi name requires location permission (and active location services) since Android 8.1
                    // see https://issuetracker.google.com/issues/70633700
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(context, SettingsActivity::class.java)
                        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, settings.account)
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

        protected fun notifyPermissions(intent: Intent) {
            val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_ERRORS)
                    .setSmallIcon(R.drawable.ic_sync_problem_notify)
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
