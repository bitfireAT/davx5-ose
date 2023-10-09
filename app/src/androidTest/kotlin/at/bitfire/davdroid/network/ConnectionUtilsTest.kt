/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class ConnectionUtilsTest {

    private val connectivityManager = mockk<ConnectivityManager>()
    private val network1 = mockk<Network>()
    private val network2 = mockk<Network>()
    private val capabilities = mockk<NetworkCapabilities>()

    @Before
    fun setUp() {
        every { connectivityManager.allNetworks } returns arrayOf(network1, network2)
        every { connectivityManager.getNetworkInfo(network1) } returns mockk()
        every { connectivityManager.getNetworkInfo(network2) } returns mockk()
        every { connectivityManager.getNetworkCapabilities(network1) } returns capabilities
        every { connectivityManager.getNetworkCapabilities(network2) } returns capabilities
    }

    @Test
    fun testWifiAvailable_capabilitiesNull() {
        every { connectivityManager.getNetworkCapabilities(network1) } returns null
        every { connectivityManager.getNetworkCapabilities(network2) } returns null
        assertFalse(ConnectionUtils.wifiAvailable(connectivityManager))
    }

    @Test
    fun testWifiAvailable() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false
        assertFalse(ConnectionUtils.wifiAvailable(connectivityManager))
    }

    @Test
    fun testWifiAvailable_wifi() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false
        assertFalse(ConnectionUtils.wifiAvailable(connectivityManager))
    }

    @Test
    fun testWifiAvailable_validated() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertFalse(ConnectionUtils.wifiAvailable(connectivityManager))
    }

    @Test
    fun testWifiAvailable_wifiValidated() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertTrue(ConnectionUtils.wifiAvailable(connectivityManager))
    }


    @Test
    fun testInternetAvailable_capabilitiesNull() {
        every { connectivityManager.getNetworkCapabilities(network1) } returns null
        every { connectivityManager.getNetworkCapabilities(network2) } returns null
        assertFalse(ConnectionUtils.internetAvailable(connectivityManager, false))
    }

    @Test
    fun testInternetAvailable_Internet() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false
        assertFalse(ConnectionUtils.internetAvailable(connectivityManager, false))
    }

    @Test
    fun testInternetAvailable_Validated() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns false
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertFalse(ConnectionUtils.internetAvailable(connectivityManager, false))
    }

    @Test
    fun testInternetAvailable_InternetValidated() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertTrue(ConnectionUtils.internetAvailable(connectivityManager, false))
    }

    @Test
    fun testInternetAvailable_ignoreVpns() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_NOT_VPN) } returns false
        assertFalse(ConnectionUtils.internetAvailable(connectivityManager, true))
    }

    @Test
    fun testInternetAvailable_ignoreVpns_Notvpn() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_NOT_VPN) } returns true
        assertTrue(ConnectionUtils.internetAvailable(connectivityManager, true))
    }

    @Test
    fun testInternetAvailable_twoConnectionsFirstOneWithoutInternet() {
        // The real case that failed in davx5-ose#395 is that the connection list contains (in this order)
        // 1. a mobile network without INTERNET, but with VALIDATED
        // 2. a WiFi network with INTERNET and VALIDATED

        // The "return false" of hasINTERNET will trigger at the first connection, the
        // "andThen true" will trigger for the second connection
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns false andThen true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true

        // There is an internet connection if any(!) connection has both INTERNET and VALIDATED.
        assertTrue(ConnectionUtils.internetAvailable(connectivityManager, false))
    }

    @Test
    fun testInternetAvailable_twoConnectionsFirstOneWithoutValidated() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false andThen true
        assertTrue(ConnectionUtils.internetAvailable(connectivityManager, false))
    }

    @Test
    fun testInternetAvailable_twoConnectionsFirstOneWithoutNotvpn() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_NOT_VPN) } returns false andThen true
        assertTrue(ConnectionUtils.internetAvailable(connectivityManager, true))
    }

}