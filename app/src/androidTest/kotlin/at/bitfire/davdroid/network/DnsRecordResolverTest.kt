/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.TXTRecord
import javax.inject.Inject
import kotlin.random.Random

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
        val dns1030 = SRVRecord(
            Name.fromString("_caldavs._tcp.example.com."),
            DClass.IN, 3600, 10, 30, 8443, Name.fromString("dav1030.example.com.")
        )
        val records = arrayOf(dns1010, dns1020, dns1030)

        val randomNumberGenerator = mockk<Random>()
        for (i in 0..60) {
            every { randomNumberGenerator.nextInt(0, 61) } returns i
            val expected = when (i) {
                in 0..10 -> dns1010
                in 11..30 -> dns1020
                else -> dns1030
            }
            assertEquals(expected, dnsRecordResolver.bestSRVRecord(records, randomNumberGenerator))
        }
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


    @Test
    fun testPathsFromTXTRecords_Empty() {
        assertTrue(dnsRecordResolver.pathsFromTXTRecords(arrayOf()).isEmpty())
    }

    @Test
    fun testPathsFromTXTRecords_OnePath() {
        val result = dnsRecordResolver.pathsFromTXTRecords(arrayOf(
            TXTRecord(Name.fromString("example.com."), 0, 0L, listOf("something=else", "path=/path1"))
        )).toTypedArray()
        assertArrayEquals(arrayOf("/path1"), result)
    }

    @Test
    fun testPathsFromTXTRecords_TwoPaths() {
        val result = dnsRecordResolver.pathsFromTXTRecords(arrayOf(
            TXTRecord(Name.fromString("example.com."), 0, 0L, listOf("path=/path1", "something-else", "path=/path2"))
        )).toTypedArray()
        result.sort()
        assertArrayEquals(arrayOf("/path1", "/path2"), result)
    }

}