/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.network.ConnectionUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.AccountUtils
import at.bitfire.davdroid.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class BaseSyncWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var baseSyncWorker: BaseSyncWorker

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private val accountManager by lazy { AccountManager.get(context) }
    private val account by lazy { Account("Test Account", context.getString(R.string.account_type)) }
    private val fakeCredentials = Credentials("test", "test")

    @Before
    fun setup() {
        hiltRule.inject()

        assertTrue(AccountUtils.createAccount(context, account, AccountSettings.initialUserData(fakeCredentials)))
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0)

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun teardown() {
        accountManager.removeAccountExplicitly(account)
    }


    @Test
    fun testWifiConditionsMet_withoutWifi() {
        val accountSettings = mockk<AccountSettings>()
        every { accountSettings.getSyncWifiOnly() } returns false
        assertTrue(baseSyncWorker.wifiConditionsMet(accountSettings))
    }
    
    @Test
    fun testWifiConditionsMet_anyWifi_wifiEnabled() {
        val accountSettings = accountSettingsFactory.forAccount(account)
        accountSettings.setSyncWiFiOnly(true)

        mockkObject(ConnectionUtils)
        every { ConnectionUtils.wifiAvailable(any()) } returns true
        mockkObject(BaseSyncWorker.Companion)
        every { baseSyncWorker.correctWifiSsid(any()) } returns true

        assertTrue(baseSyncWorker.wifiConditionsMet(accountSettings))
    }

    @Test
    fun testWifiConditionsMet_anyWifi_wifiDisabled() {
        val accountSettings = accountSettingsFactory.forAccount(account)
        accountSettings.setSyncWiFiOnly(true)

        mockkObject(ConnectionUtils)
        every { ConnectionUtils.wifiAvailable(any()) } returns false
        mockkObject(BaseSyncWorker.Companion)
        every { baseSyncWorker.correctWifiSsid(any()) } returns true

        assertFalse(baseSyncWorker.wifiConditionsMet(accountSettings))
    }


    @Test
    fun testCorrectWifiSsid_CorrectWiFiSsid() {
        val accountSettings = accountSettingsFactory.forAccount(account)
        mockkObject(accountSettings)
        every { accountSettings.getSyncWifiOnlySSIDs() } returns listOf("SampleWiFi1","ConnectedWiFi")

        mockkObject(PermissionUtils)
        every { PermissionUtils.canAccessWifiSsid(any()) } returns true

        val wifiManager = context.getSystemService<WifiManager>()!!
        mockkObject(wifiManager)
        every { wifiManager.connectionInfo } returns spyk<WifiInfo>().apply {
            every { ssid } returns "ConnectedWiFi"
        }

        assertTrue(baseSyncWorker.correctWifiSsid(accountSettings))
    }

    @Test
    fun testCorrectWifiSsid_WrongWiFiSsid() {
        val accountSettings = accountSettingsFactory.forAccount(account)
        mockkObject(accountSettings)
        every { accountSettings.getSyncWifiOnlySSIDs() } returns listOf("SampleWiFi1","SampleWiFi2")

        mockkObject(PermissionUtils)
        every { PermissionUtils.canAccessWifiSsid(any()) } returns true

        val wifiManager = context.getSystemService<WifiManager>()!!
        mockkObject(wifiManager)
        every { wifiManager.connectionInfo } returns spyk<WifiInfo>().apply {
            every { ssid } returns "ConnectedWiFi"
        }

        assertFalse(baseSyncWorker.correctWifiSsid(accountSettings))
    }

}