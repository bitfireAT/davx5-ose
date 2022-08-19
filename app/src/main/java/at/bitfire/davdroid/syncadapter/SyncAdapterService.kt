/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.app.Service
import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.core.content.getSystemService
import at.bitfire.davdroid.ConcurrentUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.account.WifiPermissionsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
abstract class SyncAdapterService: Service() {

    companion object {
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

    @Inject lateinit var appDatabase: AppDatabase


    protected abstract fun syncAdapter(): AbstractThreadedSyncAdapter

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!


    /**
     * Base class for our sync adapters. Guarantees that
     *
     * 1. not more than one sync adapter per account and authority is running at a time,
     * 2. `Thread.currentThread().contextClassLoader` is set to the current context's class loader.
     *
     * Also provides some useful methods that can be used by derived sync adapters.
     */
    abstract class SyncAdapter(
        context: Context,
        val db: AppDatabase
    ): AbstractThreadedSyncAdapter(
        context,
        true    // isSyncable shouldn't be -1 because DAVx5 sets it to 0 or 1.
                            // However, if it is -1 by accident, set it to 1 to avoid endless sync loops.
    ) {

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


        abstract fun sync(account: Account, extras: Bundle, authority: String, httpClient: Lazy<HttpClient>, provider: ContentProviderClient, syncResult: SyncResult)

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            Logger.log.log(Level.INFO, "$authority sync of $account has been initiated", extras.keySet().joinToString(", "))

            // prevent multiple syncs of the same authority and account to run simultaneously
            val currentSyncKey = Pair(authority, account)
            if (ConcurrentUtils.runSingle(currentSyncKey) {
                // required for ServiceLoader -> ical4j -> ical4android
                Thread.currentThread().contextClassLoader = context.classLoader

                val accountSettings by lazy { AccountSettings(context, account) }
                val httpClient = lazy { HttpClient.Builder(context, accountSettings).build() }

                try {
                    val runSync = /* always true in open-source edition */ true

                    if (runSync)
                        sync(account, extras, authority, httpClient, provider, syncResult)

                } catch (e: InvalidAccountException) {
                    Logger.log.log(
                        Level.WARNING,
                        "Account was removed during synchronization",
                        e
                    )
                } finally {
                    if (httpClient.isInitialized())
                        httpClient.value.close()
                }
            })
                Logger.log.log(Level.INFO, "Sync for $currentSyncKey finished", syncResult)
            else
                Logger.log.warning("There's already another running sync for $currentSyncKey, aborting")
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception when opening content provider for $authority")
        }

        override fun onSyncCanceled() {
            Logger.log.info("Sync thread cancelled! Interrupting sync")
            super.onSyncCanceled()
        }

        override fun onSyncCanceled(thread: Thread) {
            Logger.log.info("Sync thread ${thread.id} cancelled! Interrupting sync")
            super.onSyncCanceled(thread)
        }


        protected fun checkSyncConditions(settings: AccountSettings): Boolean {
            if (settings.getSyncWifiOnly()) {
                // WiFi required
                val connectivityManager = context.getSystemService<ConnectivityManager>()!!

                // check for connected WiFi network
                var wifiAvailable = false
                connectivityManager.allNetworks.forEach { network ->
                    connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                            wifiAvailable = true
                    }
                }
                if (!wifiAvailable) {
                    Logger.log.info("Not on connected WiFi, stopping")
                    return false
                }
                // if execution reaches this point, we're on a connected WiFi

                settings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
                    // check required permissions and location status
                    if (!PermissionUtils.canAccessWifiSsid(context)) {
                        // not all permissions granted; show notification
                        val intent = Intent(context, WifiPermissionsActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, settings.account)
                        PermissionUtils.notifyPermissions(context, intent)

                        Logger.log.warning("Can't access WiFi SSID, aborting sync")
                        return false
                    }

                    val wifi = context.getSystemService<WifiManager>()!!
                    val info = wifi.connectionInfo
                    if (info == null || !onlySSIDs.contains(info.ssid.trim('"'))) {
                        Logger.log.info("Connected to wrong WiFi network (${info.ssid}), aborting sync")
                        return false
                    } else
                        Logger.log.fine("Connected to WiFi network ${info.ssid}")
                }
            }
            return true
        }

    }

}