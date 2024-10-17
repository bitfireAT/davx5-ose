/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.davdroid.ui.account.WifiPermissionsActivity
import at.bitfire.davdroid.util.PermissionUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Provides methods to check whether a sync shall be run for a given account.
 */
class SyncConditions @AssistedInject constructor(
    @Assisted private val accountSettings: AccountSettings,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry
) {

    @AssistedFactory
    interface Factory {
        fun create(accountSettings: AccountSettings): SyncConditions
    }


    /**
     * Checks whether we are connected to the correct wifi (SSID) defined by user in the
     * account settings.
     *
     * Note: Should be connected to some wifi before calling.
     *
     * @return *true* if connected to the correct wifi OR no wifi names were specified in
     * account settings; *false* otherwise
     */
    internal fun correctWifiSsid(): Boolean {
        accountSettings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
            // check required permissions and location status
            if (!PermissionUtils.canAccessWifiSsid(context)) {
                // not all permissions granted; show notification
                val intent = Intent(context, WifiPermissionsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, accountSettings.account)
                notificationRegistry.notifyPermissions(intent)

                logger.warning("Can't access WiFi SSID, aborting sync")
                return false
            }

            val wifi = context.getSystemService<WifiManager>()!!
            @Suppress("DEPRECATION") val info = wifi.connectionInfo
            if (info == null || !onlySSIDs.contains(info.ssid.trim('"'))) {
                logger.info("Connected to wrong WiFi network (${info.ssid}), aborting sync")
                return false
            }
            logger.fine("Connected to WiFi network ${info.ssid}")
        }
        return true
    }

    /**
     * Checks whether we are connected to the Internet.
     *
     * On API 26+ devices, if a VPN is used, WorkManager might start the SyncWorker without an
     * Internet connection (because [NetworkCapabilities.NET_CAPABILITY_VALIDATED] is always set for VPN connections).
     * To prevent the start without internet access, we don't check for VPN connections by default
     * (by using [NetworkCapabilities.NET_CAPABILITY_NOT_VPN]).
     *
     * However in special occasions (when syncing over a VPN without validated Internet on the
     * underlying connection) we do not want to exclude VPNs.
     *
     * This method uses [AccountSettings.getIgnoreVpns]: if `true`, it filters VPN connections in the Internet check;
     * `false` allows them as valid connection.
     *
     * @return whether we are connected to the Internet
     */
    internal fun internetAvailable(): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        @Suppress("DEPRECATION")
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            logger.log(
                Level.FINE, "Looking for validated Internet over this connection.",
                arrayOf(connectivityManager.getNetworkInfo(network), capabilities)
            )

            if (capabilities != null) {
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    logger.fine("Missing network capability: INTERNET")
                    return@any false
                }

                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    logger.fine("Missing network capability: VALIDATED")
                    return@any false
                }

                val ignoreVpns = accountSettings.getIgnoreVpns()
                if (ignoreVpns)
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                        logger.fine("Missing network capability: NOT_VPN")
                        return@any false
                    }

                logger.fine("This connection can be used.")
                /* return@any */ true
            } else
            // no network capabilities available, we can't use this connection
            /* return@any */ false
        }
    }

    /**
     * Checks whether we are connected to validated WiFi
     */
    internal fun wifiAvailable(): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        @Suppress("DEPRECATION")
        connectivityManager.allNetworks.forEach { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    return true
            }
        }
        return false
    }

    /**
     * Checks whether user imposed sync conditions from settings are met:
     * - Sync only on WiFi?
     * - Sync only on specific WiFi (SSID)?
     *
     * @return *true* if conditions are met; *false* if not
     */
    fun wifiConditionsMet(): Boolean {
        // May we sync without WiFi?
        if (!accountSettings.getSyncWifiOnly())
            return true     // yes, continue

        // WiFi required, is it available?
        if (!wifiAvailable()) {
            logger.info("Not on connected WiFi, stopping")
            return false
        }
        // If execution reaches this point, we're on a connected WiFi

        // Check whether we are connected to the correct WiFi (in case SSID was provided)
        return correctWifiSsid()
    }

}