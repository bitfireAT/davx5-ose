/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.SyncConditions
import at.bitfire.davdroid.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SyncConditionsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var capabilities: NetworkCapabilities

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var factory: SyncConditions.Factory

    @MockK
    lateinit var network1: Network

    @MockK
    lateinit var network2: Network


    private lateinit var accountSettings: AccountSettings

    private lateinit var conditions: SyncConditions

    private lateinit var connectivityManager: ConnectivityManager

    @Before
    fun setup() {
        hiltRule.inject()

        // prepare accountSettings with some necessary data
        accountSettings = mockk<AccountSettings> {
            every { account } returns Account("test", "test")
            every { getIgnoreVpns() } returns false     // default value
        }

        conditions = factory.create(accountSettings)

        connectivityManager = context.getSystemService<ConnectivityManager>()!!.also { cm ->
            mockkObject(cm)
            every { cm.allNetworks } returns arrayOf(network1, network2)
            every { cm.getNetworkInfo(network1) } returns mockk()
            every { cm.getNetworkInfo(network2) } returns mockk()
            every { cm.getNetworkCapabilities(network1) } returns capabilities
            every { cm.getNetworkCapabilities(network2) } returns capabilities
        }
    }


    @Test
    fun testCorrectWifiSsid_CorrectWiFiSsid() {
        every { accountSettings.getSyncWifiOnlySSIDs() } returns listOf("SampleWiFi1","ConnectedWiFi")

        mockkObject(PermissionUtils)
        every { PermissionUtils.canAccessWifiSsid(any()) } returns true

        val wifiManager = context.getSystemService<WifiManager>()!!
        mockkObject(wifiManager)
        every { wifiManager.connectionInfo } returns spyk<WifiInfo>().apply {
            every { ssid } returns "ConnectedWiFi"
        }

        assertTrue(conditions.correctWifiSsid())
    }

    @Test
    fun testCorrectWifiSsid_WrongWiFiSsid() {
        every { accountSettings.getSyncWifiOnlySSIDs() } returns listOf("SampleWiFi1","SampleWiFi2")

        mockkObject(PermissionUtils)
        every { PermissionUtils.canAccessWifiSsid(any()) } returns true

        val wifiManager = context.getSystemService<WifiManager>()!!
        mockkObject(wifiManager)
        every { wifiManager.connectionInfo } returns spyk<WifiInfo>().apply {
            every { ssid } returns "ConnectedWiFi"
        }

        assertFalse(conditions.correctWifiSsid())
    }


    @Test
    fun testInternetAvailable_capabilitiesNull() {
        every { connectivityManager.getNetworkCapabilities(network1) } returns null
        every { connectivityManager.getNetworkCapabilities(network2) } returns null
        assertFalse(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_Internet() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false
        assertFalse(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_Validated() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns false
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertFalse(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_InternetValidated() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertTrue(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_ignoreVpns() {
        every { accountSettings.getIgnoreVpns() } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_NOT_VPN) } returns false
        assertFalse(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_ignoreVpns_NotVpn() {
        every { accountSettings.getIgnoreVpns() } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_NOT_VPN) } returns true
        assertTrue(conditions.internetAvailable())
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
        assertTrue(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_twoConnectionsFirstOneWithoutValidated() {
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false andThen true
        assertTrue(conditions.internetAvailable())
    }

    @Test
    fun testInternetAvailable_twoConnectionsFirstOneWithoutNotVpn() {
        every { accountSettings.getIgnoreVpns() } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_NOT_VPN) } returns false andThen true
        assertTrue(conditions.internetAvailable())
    }
    

    @Test
    fun testWifiAvailable_capabilitiesNull() {
        every { connectivityManager.getNetworkCapabilities(network1) } returns null
        every { connectivityManager.getNetworkCapabilities(network2) } returns null
        assertFalse(conditions.wifiAvailable())
    }

    @Test
    fun testWifiAvailable() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false
        assertFalse(conditions.wifiAvailable())
    }

    @Test
    fun testWifiAvailable_wifi() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns false
        assertFalse(conditions.wifiAvailable())
    }

    @Test
    fun testWifiAvailable_validated() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns false
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertFalse(conditions.wifiAvailable())
    }

    @Test
    fun testWifiAvailable_wifiValidated() {
        every { capabilities.hasTransport(TRANSPORT_WIFI) } returns true
        every { capabilities.hasCapability(NET_CAPABILITY_VALIDATED) } returns true
        assertTrue(conditions.wifiAvailable())
    }


    @Test
    fun testWifiConditionsMet_withoutWifi() {
        // "Sync only over Wi-Fi" is disabled
        every { accountSettings.getSyncWifiOnly() } returns false

        assertTrue(factory.create(accountSettings).wifiConditionsMet())
    }

    @Test
    fun testWifiConditionsMet_anyWifi_wifiEnabled() {
        // "Sync only over Wi-Fi" is enabled
        every { accountSettings.getSyncWifiOnly() } returns true

        // Wi-Fi is available
        mockkObject(conditions) {
            // Wi-Fi is available
            every { conditions.wifiAvailable() } returns true

            // Wi-Fi SSID is correct
            every { conditions.correctWifiSsid() } returns true

            assertTrue(conditions.wifiConditionsMet())
        }
    }

    @Test
    fun testWifiConditionsMet_anyWifi_wifiDisabled() {
        // "Sync only over Wi-Fi" is enabled
        every { accountSettings.getSyncWifiOnly() } returns true

        mockkObject(conditions) {
            // Wi-Fi is not available
            every { conditions.wifiAvailable() } returns false

            // Wi-Fi SSID is correct
            every { conditions.correctWifiSsid() } returns true

            assertFalse(conditions.wifiConditionsMet())
        }
    }
}