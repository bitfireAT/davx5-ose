/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import at.bitfire.davdroid.log.Logger
import okhttp3.HttpUrl
import org.xbill.DNS.*
import java.util.*

/**
 * Some WebDAV and related network utility methods
 */
object DavUtils {

    fun ARGBtoCalDAVColor(colorWithAlpha: Int): String {
        val alpha = (colorWithAlpha shr 24) and 0xFF
        val color = colorWithAlpha and 0xFFFFFF
        return String.format("#%06X%02X", color, alpha)
    }

    fun lastSegmentOfUrl(url: HttpUrl): String {
        // the list returned by HttpUrl.pathSegments() is unmodifiable, so we have to create a copy
        val segments = LinkedList<String>(url.pathSegments())
        segments.reverse()

        return segments.firstOrNull { it.isNotEmpty() } ?: "/"
    }

    fun prepareLookup(context: Context, lookup: Lookup) {
        @TargetApi(Build.VERSION_CODES.O)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /* Since Android 8, the system properties net.dns1, net.dns2, ... are not available anymore.
               The current version of dnsjava relies on these properties to find the default name servers,
               so we have to add the servers explicitly (fortunately, there's an Android API to
               get the active DNS servers). */
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeLink = connectivity.getLinkProperties(connectivity.activeNetwork)
            val simpleResolvers = activeLink.dnsServers.map {
                Logger.log.fine("Using DNS server ${it.hostAddress}")
                val resolver = SimpleResolver()
                resolver.setAddress(it)
                resolver
            }
            val resolver = ExtendedResolver(simpleResolvers.toTypedArray())
            lookup.setResolver(resolver)
        }
    }

    fun selectSRVRecord(records: Array<Record>?): SRVRecord? {
        val srvRecords = records?.filterIsInstance(SRVRecord::class.java)
        srvRecords?.let {
            if (it.size > 1)
                Logger.log.warning("Multiple SRV records not supported yet; using first one")
            return it.firstOrNull()
        }
        return null
    }

    fun pathsFromTXTRecords(records: Array<Record>?): List<String> {
        val paths = LinkedList<String>()
        records?.filterIsInstance(TXTRecord::class.java)?.forEach { txt ->
            @Suppress("UNCHECKED_CAST")
            for (segment in txt.strings as List<String>)
                if (segment.startsWith("path=")) {
                    paths.add(segment.substring(5))
                    break
                }
        }
        return paths
    }

}
