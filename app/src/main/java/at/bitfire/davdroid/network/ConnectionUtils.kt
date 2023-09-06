/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

object ConnectionUtils {

    /**
     * Checks whether we are connected to working WiFi
     */
    internal fun wifiAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
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
     * Checks whether we are connected to the Internet.
     *
     * On API 26+ devices, if a VPN is used, WorkManager might start the SyncWorker without an
     * internet connection (because NET_CAPABILITY_VALIDATED is always set for VPN connections).
     * To prevent the start without internet access, we don't check for VPN connections by default
     * (by using [NetworkCapabilities.NET_CAPABILITY_NOT_VPN]).
     *
     * However in special occasions (when syncing over a VPN without validated Internet on the
     * underlying connection) we do not want to exclude VPNs.
     *
     * @param ignoreVpns *true* filters VPN connections in the Internet check; *false* allows them as valid connection
     * @return whether we are connected to the Internet
     */
    @RequiresApi(23)
    internal fun internetAvailable(context: Context, ignoreVpns: Boolean): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            Logger.log.log(Level.FINE, "Looking for validated Internet over this connection.",
                arrayOf(connectivityManager.getNetworkInfo(network), capabilities))

            if (capabilities != null) {
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    Logger.log.fine("Missing network capability: INTERNET")
                    return@any false
                }

                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    Logger.log.fine("Missing network capability: VALIDATED")
                    return@any false
                }

                if (ignoreVpns)
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                        Logger.log.fine("Missing network capability: NOT_VPN")
                        return@any false
                    }

                Logger.log.fine("This connection can be used.")
                /* return@any */ true
            } else
                // no network capabilities available, we can't use this connection
                /* return@any */ false
        }
    }

}