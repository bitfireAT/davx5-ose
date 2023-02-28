/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import at.bitfire.davdroid.Android10Resolver
import at.bitfire.davdroid.log.Logger
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import org.xbill.DNS.*
import java.net.InetAddress
import java.util.*

/**
 * Some WebDAV and HTTP network utility methods.
  */
object DavUtils {

    val DNS_QUAD9 = InetAddress.getByAddress(byteArrayOf(9,9,9,9))

    const val MIME_TYPE_ACCEPT_ALL = "*/*"

    val MEDIA_TYPE_JCARD = "application/vcard+json".toMediaType()
    val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream".toMediaType()
    val MEDIA_TYPE_VCARD = "text/vcard".toMediaType()


    @Suppress("FunctionName")
    fun ARGBtoCalDAVColor(colorWithAlpha: Int): String {
        val alpha = (colorWithAlpha shr 24) and 0xFF
        val color = colorWithAlpha and 0xFFFFFF
        return String.format(Locale.ROOT, "#%06X%02X", color, alpha)
    }

    fun lastSegmentOfUrl(url: HttpUrl): String {
        // the list returned by HttpUrl.pathSegments() is unmodifiable, so we have to create a copy
        val segments = LinkedList(url.pathSegments)
        segments.reverse()

        return segments.firstOrNull { it.isNotEmpty() } ?: "/"
    }

    fun prepareLookup(context: Context, lookup: Lookup) {
        if (Build.VERSION.SDK_INT >= 29) {
            /* Since Android 10, there's a native DnsResolver API that allows to send SRV queries without
               knowing which DNS servers have to be used. DNS over TLS is now also supported. */
            Logger.log.fine("Using Android 10+ DnsResolver")
            lookup.setResolver(Android10Resolver)

        } else if (Build.VERSION.SDK_INT >= 26) {
            /* Since Android 8, the system properties net.dns1, net.dns2, ... are not available anymore.
               The current version of dnsjava relies on these properties to find the default name servers,
               so we have to add the servers explicitly (fortunately, there's an Android API to
               get the DNS servers of the network connections). */
           val dnsServers = LinkedList<InetAddress>()

            val connectivity = context.getSystemService<ConnectivityManager>()!!
            connectivity.allNetworks.forEach { network ->
                val active = connectivity.getNetworkInfo(network)?.isConnected ?: false
                connectivity.getLinkProperties(network)?.let { link ->
                    if (active)
                        // active connection, insert at top of list
                        dnsServers.addAll(0, link.dnsServers)
                    else
                        // inactive connection, insert at end of list
                        dnsServers.addAll(link.dnsServers)
                }
            }

            // fallback: add Quad9 DNS in case that no other DNS works
            dnsServers.add(DNS_QUAD9)

            val uniqueDnsServers = LinkedHashSet<InetAddress>(dnsServers)
            val simpleResolvers = uniqueDnsServers.map { dns ->
                Logger.log.fine("Adding DNS server ${dns.hostAddress}")
                SimpleResolver().apply {
                    setAddress(dns)
                }
            }
            val resolver = ExtendedResolver(simpleResolvers.toTypedArray())
            lookup.setResolver(resolver)
        }
    }

    fun selectSRVRecord(records: Array<out Record>?): SRVRecord? {
        if (records == null)
            return null

        val srvRecords = records.filterIsInstance(SRVRecord::class.java)
        if (srvRecords.size <= 1)
            return srvRecords.firstOrNull()

        /* RFC 2782

           Priority
                The priority of this target host.  A client MUST attempt to
                contact the target host with the lowest-numbered priority it can
                reach; target hosts with the same priority SHOULD be tried in an
                order defined by the weight field. [...]

           Weight
                A server selection mechanism.  The weight field specifies a
                relative weight for entries with the same priority. [...]

                To select a target to be contacted next, arrange all SRV RRs
                (that have not been ordered yet) in any order, except that all
                those with weight 0 are placed at the beginning of the list.

                Compute the sum of the weights of those RRs, and with each RR
                associate the running sum in the selected order. Then choose a
                uniform random number between 0 and the sum computed
                (inclusive), and select the RR whose running sum value is the
                first in the selected order which is greater than or equal to
                the random number selected. The target host specified in the
                selected SRV RR is the next one to be contacted by the client.
        */
        val minPriority = srvRecords.map { it.priority }.minOrNull()
        val useableRecords = srvRecords.filter { it.priority == minPriority }.sortedBy { it.weight != 0 }

        val map = TreeMap<Int, SRVRecord>()
        var runningWeight = 0
        for (record in useableRecords) {
            val weight = record.weight
            runningWeight += weight
            map[runningWeight] = record
        }

        val selector = (0..runningWeight).random()
        return map.ceilingEntry(selector)!!.value
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


    // extension methods

    /**
     * Compares MIME type and subtype of two MediaTypes. Does _not_ compare parameters
     * like `charset` or `version`.
     *
     * @param other   MediaType to compare with
     *
     * @return *true* if type and subtype match; *false* if they don't
     */
    fun MediaType.sameTypeAs(other: MediaType) =
        type == other.type && subtype == other.subtype

}
