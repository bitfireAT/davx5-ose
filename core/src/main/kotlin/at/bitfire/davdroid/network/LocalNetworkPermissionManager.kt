/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject

/**
 * Android 17 introduced a new permission model for local network access.
 * To know whether we should actually request the permission for an arbitrary address, this class provides some utility
 * functions to determine whether an address is considered "local" by Android 17's definition.
 * @see <a href="https://developer.android.com/privacy-and-security/local-network-definition">Local Network Definition</a>
 * @see <a href="https://developer.android.com/privacy-and-security/local-network-permission">Local Network Permission</a>
 */
class LocalNetworkPermissionManager @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Checks whether the given hostname is considered "local" by Android 17's definition.
     * Some complex setups may falsely return `true` for local addresses if the permission is already granted.
     * @throws java.net.UnknownHostException If the domain doesn't exist or DNS is unavailable
     * @throws java.net.SocketException If the device's routing table cannot be read
     */
    suspend fun isAndroid17LocalNetwork(hostname: String): Boolean {
        // Resolve the hostname to an IP address (this may involve DNS resolution)
        // Note that DNS resolution is excluded from the permission check so that we can actually perform this check
        val ip = withContext(ioDispatcher) { InetAddress.getByName(hostname) }

        // Check the hardcoded standard ranges (RFC1918, CGNAT, Multicast, etc.)
        if (isStandardLocalIp(ip)) return true

        // Check the device's actual dynamic routing table across all interfaces
        return isDirectlyConnectedRoute(ip)
    }

    private fun isDirectlyConnectedRoute(ip: InetAddress): Boolean {
        // Iterate through all connected networks (Wi-Fi, Ethernet, Thread, etc.),
        // not just the default internet-providing network.
        for (network in cm.allNetworks) {
            val capabilities = cm.getNetworkCapabilities(network) ?: continue

            // Android 17 Local Network Protection explicitly EXCLUDES Cellular and VPNs.
            // Skip checking routes on these transports.
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                continue
            }

            val linkProperties = cm.getLinkProperties(network) ?: continue

            // Iterate through all active routes on this connection
            for (route in linkProperties.routes) {
                val destination = route.destination // This is an IpPrefix (e.g., 192.0.2.0/24)

                // Ignore the default gateway route (0.0.0.0/0 or ::/0) which catches all internet traffic
                if (destination.prefixLength == 0) continue

                // If the route's subnet contains our target IP, it is a directly-connected local route
                if (destination.contains(ip)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isStandardLocalIp(ip: InetAddress): Boolean {
        // 1. Check Multicast (224.0.0.0/4 for IPv4, ff00::/8 for IPv6)
        if (ip.isMulticastAddress) return true

        val bytes = ip.address

        if (ip is Inet4Address) {
            // Broadcast (255.255.255.255)
            if (bytes.contentEquals(byteArrayOf(-1, -1, -1, -1))) return true

            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF

            return when {
                // RFC1918: 10.0.0.0/8
                a == 10 -> true
                // RFC1918: 172.16.0.0/12
                a == 172 && b in 16..31 -> true
                // RFC1918: 192.168.0.0/16
                a == 192 && b == 168 -> true
                // Link-local: 169.254.0.0/16
                a == 169 && b == 254 -> true
                // CGNAT: 100.64.0.0/10
                a == 100 && b in 64..127 -> true
                else -> false
            }
        } else if (ip is Inet6Address) {
            // IPv6 Link-local
            if (ip.isLinkLocalAddress) return true
        }

        return false
    }
}
