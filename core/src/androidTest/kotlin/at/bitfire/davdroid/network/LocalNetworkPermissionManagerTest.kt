/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address

@RunWith(AndroidJUnit4::class)
class LocalNetworkPermissionManagerTest {

    private lateinit var context: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var manager: LocalNetworkPermissionManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create a real LocalNetworkPermissionManager but with mocked ConnectivityManager
        mockConnectivityManager = mockk(relaxed = true)
        
        // We need to inject the mock ConnectivityManager
        manager = LocalNetworkPermissionManager(
            context = createMockContext(mockConnectivityManager),
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Creates a mock Context that returns our mock ConnectivityManager
     */
    private fun createMockContext(mockCm: ConnectivityManager): Context {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockCm
        return mockContext
    }

    @Test
    fun `isAndroid17LocalNetwork returns true for standard local IP`() = runTest {
        // Standard local IPs should be detected without checking routes
        assert(manager.isAndroid17LocalNetwork("192.168.1.1"))
        assert(manager.isAndroid17LocalNetwork("10.0.0.1"))
        assert(manager.isAndroid17LocalNetwork("172.16.0.1"))
    }

    @Test
    fun `isAndroid17LocalNetwork returns false for public IP not in routing table`() = runTest {
        // Mock an empty routing table
        every { mockConnectivityManager.allNetworks } returns emptyArray()
        
        // Public IPs should return false when not in routing table
        assert(!manager.isAndroid17LocalNetwork("8.8.8.8"))
    }

    @Test
    fun `isAndroid17LocalNetwork returns true for non-standard IP in local routing table`() = runTest {
        // Create a mock network with a route to a non-standard local IP
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        val mockLinkProperties = mockk<LinkProperties>(relaxed = true)
        
        // Make it a WiFi network (not cellular or VPN)
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        
        // Create a mock route that contains our test IP (192.0.2.1 - TEST-NET-1)
        val testIp = Inet4Address.getByName("192.0.2.1")
        val mockRoute = mockk<RouteInfo>(relaxed = true)
        val mockIpPrefix = mockk<IpPrefix>(relaxed = true)
        
        every { mockRoute.destination } returns mockIpPrefix
        every { mockIpPrefix.prefixLength } returns 24  // Not 0 (not default route)
        every { mockIpPrefix.contains(testIp) } returns true
        
        every { mockLinkProperties.routes } returns listOf(mockRoute)
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockConnectivityManager.getLinkProperties(mockNetwork) } returns mockLinkProperties
        every { mockConnectivityManager.allNetworks } returns arrayOf(mockNetwork)
        
        // Even though 192.0.2.1 is not a standard local IP, it's in the routing table
        // so it should be detected as local
        assert(manager.isAndroid17LocalNetwork("192.0.2.1"))
    }

    @Test
    fun `isAndroid17LocalNetwork ignores cellular networks`() = runTest {
        // Create a mock cellular network
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        val mockLinkProperties = mockk<LinkProperties>(relaxed = true)
        
        // Make it a cellular network
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        
        // Create a route
        val testIp = Inet4Address.getByName("192.0.2.1")
        val mockRoute = mockk<RouteInfo>(relaxed = true)
        val mockIpPrefix = mockk<IpPrefix>(relaxed = true)
        
        every { mockRoute.destination } returns mockIpPrefix
        every { mockIpPrefix.prefixLength } returns 24
        every { mockIpPrefix.contains(testIp) } returns true
        
        every { mockLinkProperties.routes } returns listOf(mockRoute)
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockConnectivityManager.getLinkProperties(mockNetwork) } returns mockLinkProperties
        every { mockConnectivityManager.allNetworks } returns arrayOf(mockNetwork)
        
        // Even though the IP is in the cellular network's routing table,
        // it should be ignored because cellular networks are excluded
        assert(!manager.isAndroid17LocalNetwork("192.0.2.1"))
    }

    @Test
    fun `isAndroid17LocalNetwork ignores VPN networks`() = runTest {
        // Create a mock VPN network
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        val mockLinkProperties = mockk<LinkProperties>(relaxed = true)
        
        // Make it a VPN network
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns true
        
        // Create a route
        val testIp = Inet4Address.getByName("192.0.2.1")
        val mockRoute = mockk<RouteInfo>(relaxed = true)
        val mockIpPrefix = mockk<IpPrefix>(relaxed = true)
        
        every { mockRoute.destination } returns mockIpPrefix
        every { mockIpPrefix.prefixLength } returns 24
        every { mockIpPrefix.contains(testIp) } returns true
        
        every { mockLinkProperties.routes } returns listOf(mockRoute)
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockConnectivityManager.getLinkProperties(mockNetwork) } returns mockLinkProperties
        every { mockConnectivityManager.allNetworks } returns arrayOf(mockNetwork)
        
        // Even though the IP is in the VPN network's routing table,
        // it should be ignored because VPN networks are excluded
        assert(!manager.isAndroid17LocalNetwork("192.0.2.1"))
    }

    @Test
    fun `isAndroid17LocalNetwork ignores default route`() = runTest {
        // Create a mock network with a default route
        val mockNetwork = mockk<Network>()
        val mockCapabilities = mockk<NetworkCapabilities>(relaxed = true)
        val mockLinkProperties = mockk<LinkProperties>(relaxed = true)
        
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        
        // Create a default route (0.0.0.0/0)
        val testIp = Inet4Address.getByName("8.8.8.8")
        val mockRoute = mockk<RouteInfo>(relaxed = true)
        val mockIpPrefix = mockk<IpPrefix>(relaxed = true)
        
        every { mockRoute.destination } returns mockIpPrefix
        every { mockIpPrefix.prefixLength } returns 0  // Default route
        every { mockIpPrefix.contains(testIp) } returns true
        
        every { mockLinkProperties.routes } returns listOf(mockRoute)
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockCapabilities
        every { mockConnectivityManager.getLinkProperties(mockNetwork) } returns mockLinkProperties
        every { mockConnectivityManager.allNetworks } returns arrayOf(mockNetwork)
        
        // Default route should be ignored, and since 8.8.8.8 is not a standard local IP,
        // it should return false
        assert(!manager.isAndroid17LocalNetwork("8.8.8.8"))
    }

}
