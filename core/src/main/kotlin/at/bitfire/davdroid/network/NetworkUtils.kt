/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL

object NetworkUtils {
    /**
     * Parses a URL, resolves its IP address, and determines if it requires
     * Android 17's ACCESS_LOCAL_NETWORK permission.
     */
    suspend fun requiresLocalNetworkPermission(urlString: String, context: Context): Boolean {
        // If version is below Android 17, the permission does not exist and is not required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            return false
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (isTrafficExemptedByVPN(connectivityManager)) {
            // If traffic is routed through a VPN, it generally does not trigger the ACCESS_LOCAL_NETWORK check.
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val host = url.host

                // Note: This triggers a DNS lookup
                val address = InetAddress.getByName(host)

                isLocalNetworkAddress(address)
            } catch (_: Exception) {
                // Handle malformed URLs or failed DNS resolutions
                false
            }
        }
    }

    private fun isTrafficExemptedByVPN(connectivityManager: ConnectivityManager): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        // If the active network is a VPN, traffic routed through it
        // generally does not trigger the ACCESS_LOCAL_NETWORK check.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /**
     * Checks an InetAddress against Android's local network IP definitions.
     */
    private fun isLocalNetworkAddress(address: InetAddress): Boolean {
        // 1. Loopback (127.0.0.1 / ::1) does NOT require the permission
        if (address.isLoopbackAddress) {
            return false
        }

        // 2. Standard local IP designations using built-in Java methods
        if (address.isSiteLocalAddress ||  // Covers RFC 1918 (10.x.x.x, 172.16.x.x, 192.168.x.x)
            address.isLinkLocalAddress ||  // Covers 169.254.x.x and fe80::/10
            address.isMulticastAddress     // Covers 224.x.x.x and ff00::/8
        ) {
            return true
        }

        // 3. Android-specific or edge-case IPv4 designations not fully covered above
        if (address is Inet4Address) {
            val bytes = address.address
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF

            // Carrier-Grade NAT (CGNAT): 100.64.0.0/10 (100.64.0.0 to 100.127.255.255)
            if (b0 == 100 && (b1 and 0xC0) == 64) {
                return true
            }

            // IPv4 Global Broadcast: 255.255.255.255
            if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) {
                return true
            }
        }

        return false
    }
}
