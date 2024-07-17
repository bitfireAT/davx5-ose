/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.SRVRecord
import javax.inject.Inject

@HiltAndroidTest
class DnsRecordResolverTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dnsRecordResolver: DnsRecordResolver

    @Before
    fun setup() {
        hiltRule.inject()
    }


    @Test
    fun testBestSRVRecord_Empty() {
        assertNull(dnsRecordResolver.bestSRVRecord(emptyArray()))
    }

    @Test
    fun testBestSRVRecord_MultipleRecords_Priority_Different() {
        val dns1010 = SRVRecord(
            Name.fromString("_caldavs._tcp.example.com."),
            DClass.IN, 3600, 10, 10, 8443, Name.fromString("dav1010.example.com.")
        )
        val dns2010 = SRVRecord(
            Name.fromString("_caldavs._tcp.example.com."),
            DClass.IN, 3600, 20, 20, 8443, Name.fromString("dav2010.example.com.")
        )

        // lowest priority first
        val result = dnsRecordResolver.bestSRVRecord(arrayOf(dns1010, dns2010))
        assertEquals(dns1010, result)
    }

    @Test
    fun testBestSRVRecord_MultipleRecords_Priority_Same() {
        val dns1010 = SRVRecord(
            Name.fromString("_caldavs._tcp.example.com."),
            DClass.IN, 3600, 10, 10, 8443, Name.fromString("dav1010.example.com.")
        )
        val dns1020 = SRVRecord(
            Name.fromString("_caldavs._tcp.example.com."),
            DClass.IN, 3600, 10, 20, 8443, Name.fromString("dav1020.example.com.")
        )

        // entries are selected randomly (for load balancing)
        // run 1000 times to get a good distribution
        val counts = IntArray(2)
        for (i in 0 until 1000) {
            val result = dnsRecordResolver.bestSRVRecord(arrayOf(dns1010, dns1020))

            when (result) {
                dns1010 -> counts[0]++
                dns1020 -> counts[1]++
            }
        }

        /* We had weights 10 and 20, so the distribution of 1000 tries should be roughly
            weight 10       fraction 1/3    expected count 333
            weight 20       fraction 2/3    expected count 667
         */
        assertTrue(counts[0] in 300..366)
        assertTrue(counts[1] in 633..700)
    }

    @Test
    fun testBestSRVRecord_OneRecord() {
        val dns1010 = SRVRecord(
            Name.fromString("_caldavs._tcp.example.com."),
            DClass.IN, 3600, 10, 10, 8443, Name.fromString("dav1010.example.com.")
        )
        val result = dnsRecordResolver.bestSRVRecord(arrayOf(dns1010))
        assertEquals(dns1010, result)
    }

}