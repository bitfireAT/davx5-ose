/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Lookup
import org.xbill.DNS.Record
import org.xbill.DNS.Resolver
import org.xbill.DNS.ResolverConfig
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TXTRecord
import java.net.InetAddress
import java.util.LinkedList
import java.util.TreeMap
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.random.Random

/**
 * Allows to resolve SRV/TXT records. Chooses the correct resolver, DNS servers etc.
 */
class DnsRecordResolver @Inject constructor(
    @ApplicationContext val context: Context,
    private val logger: Logger
) {

    // resolving

    /**
     * Fallback DNS server that will be used when other DNS are not known or working.
     * `9.9.9.9` belongs to Cloudflare who promise good privacy.
     */
    private val DNS_FALLBACK = InetAddress.getByAddress(byteArrayOf(9,9,9,9))

    private val resolver by lazy { chooseResolver() }

    init {
        // empty initialization for dnsjava because we set the servers for each request
        ResolverConfig.setConfigProviders(listOf())
    }

    /**
     * Creates a matching Resolver, depending on the Android version:
     *
     * Android 10+: Android10Resolver, which uses the raw DNS resolver that comes with Android
     * Android <10: ExtendedResolver, which uses the known DNS servers to resolve DNS queries
     */
    private fun chooseResolver(): Resolver =
        if (Build.VERSION.SDK_INT >= 29) {
            /* Since Android 10, there's a native DnsResolver API that allows to send SRV queries without
               knowing which DNS servers have to be used. DNS over TLS is now also supported. */
            logger.fine("Using Android 10+ DnsResolver")
            Android10Resolver()

        } else {
            /* Since Android 8, the system properties net.dns1, net.dns2, ... are not available anymore.
               The current version of dnsjava relies on these properties to find the default name servers,
               so we have to add the servers explicitly (fortunately, there's an Android API to
               get the DNS servers of the network connections). */
            val dnsServers = LinkedList<InetAddress>()

            val connectivity = context.getSystemService<ConnectivityManager>()!!
            @Suppress("DEPRECATION")
            connectivity.allNetworks.forEach { network ->
                val active = connectivity.getNetworkInfo(network)?.isConnected == true
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
            dnsServers.add(DNS_FALLBACK)

            val uniqueDnsServers = LinkedHashSet<InetAddress>(dnsServers)
            val simpleResolvers = uniqueDnsServers.map { dns ->
                logger.fine("Adding DNS server ${dns.hostAddress}")
                SimpleResolver(dns)
            }

            // combine SimpleResolvers which query one DNS server each to an ExtendedResolver
            ExtendedResolver(simpleResolvers.toTypedArray())
        }

    fun resolve(query: String, type: Int): Array<out Record> {
        val lookup = Lookup(query, type)
        lookup.setResolver(resolver)
        return lookup.run().orEmpty()
    }


    // record selection

    /**
     * Selects the best SRV record from a list of records, based on algorithm from RFC 2782.
     *
     * @param records the records to choose from
     * @param randomGenerator a random number generator to use for random selection
     * @return the best SRV record, or `null` if no SRV record is available
     */
    fun bestSRVRecord(records: Array<out Record>, randomGenerator: Random = Random.Default): SRVRecord? {
        val srvRecords = records.filterIsInstance<SRVRecord>()
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

        // Select records which have the minimum priority
        val minPriority = srvRecords.minOfOrNull { it.priority }
        val usableRecords = srvRecords.filter { it.priority == minPriority }
            .sortedBy { it.weight != 0 }    // and put those with weight 0 first

        val map = TreeMap<Int, SRVRecord>()
        var runningWeight = 0
        for (record in usableRecords) {
            val weight = record.weight
            runningWeight += weight
            map[runningWeight] = record
        }

        val selector = (0..runningWeight).random(randomGenerator)
        return map.ceilingEntry(selector)!!.value
    }

    fun pathsFromTXTRecords(records: Array<out Record>): List<String> {
        val paths = LinkedList<String>()
        records.filterIsInstance<TXTRecord>().forEach { txt ->
            for (segment in txt.strings as List<String>)
                if (segment.startsWith("path="))
                    paths.add(segment.substring(5))
        }
        return paths
    }

}