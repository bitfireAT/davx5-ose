/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.settings.AccountSettings.Companion.CREDENTIALS_LOCK
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SYNC_INTERVAL_ADDRESSBOOKS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SYNC_INTERVAL_CALENDARS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SYNC_INTERVAL_TASKS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_WIFI_ONLY
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsTest {
    private class TestContext(
        val accountSettings: AccountSettings,
        val store: AccountSettingsStore,
        val settingsManager: SettingsManager,
        val automaticSyncManager: AutomaticSyncManager
    )

    private fun runTest(
        storage: Map<String, String> = emptyMap(),
        sensitiveStorage: Map<String, SensitiveString> = emptyMap(),
        block: TestContext.() -> Unit
    ) {
        val store = InMemorySettingsStore(storage, sensitiveStorage)
        val automaticSyncManager: AutomaticSyncManager = mockk(relaxed = true)
        val settingsManager: SettingsManager = mockk(relaxed = true)
        val accountSettings = AccountSettings(
            accountId = LegacyAccount(Account("test", "test")),
            store = store,
            automaticSyncManager = automaticSyncManager,
            settingsManager = settingsManager
        )
        block(
            TestContext(accountSettings, store, settingsManager, automaticSyncManager)
        )
    }

    @Test
    fun test_credentials_get() = runTest(
        storage = mapOf(
            AccountSettings.KEY_USERNAME to "username",
            AccountSettings.KEY_CERTIFICATE_ALIAS to "certificateAlias",
        ),
        sensitiveStorage = mapOf(
            AccountSettings.KEY_PASSWORD to "password".toSensitiveString()
        )
    ) {
        // Validate it fetches the stored values
        val credentials = accountSettings.credentials()
        assertEquals(credentials.username, "username")
        assertEquals(credentials.password, "password".toSensitiveString())
        assertEquals(credentials.certificateAlias, "certificateAlias")
        assertNull(credentials.authState)
    }

    @Test
    fun test_credentials_set() = runTest {
        // Validate it stores the given values
        accountSettings.credentials(
            Credentials(
                username = "username",
                password = "password".toSensitiveString(),
                certificateAlias = "certificateAlias"
            )
        )
        assertEquals("username", store.getValue(AccountSettings.KEY_USERNAME))
        assertEquals("password".toSensitiveString(), store.getSensitiveValue(AccountSettings.KEY_PASSWORD))
        assertEquals("certificateAlias", store.getValue(AccountSettings.KEY_CERTIFICATE_ALIAS))
        assertNull(store.getValue(AccountSettings.KEY_AUTH_STATE))
    }

    @Test
    fun test_changingCredentialsAllowed() = runTest {
        // Mock the value, verify it works correctly
        every { settingsManager.getIntOrNull(CREDENTIALS_LOCK) } returns 0
        assertTrue(accountSettings.changingCredentialsAllowed())
        verify { settingsManager.getIntOrNull(CREDENTIALS_LOCK) }
    }

    @Test
    fun test_getSyncInterval() = runTest(
        storage = mapOf(
            KEY_SYNC_INTERVAL_ADDRESSBOOKS to "1",
            KEY_SYNC_INTERVAL_CALENDARS to "2",
            KEY_SYNC_INTERVAL_TASKS to "3"
        )
    ) {
        assertEquals(1L, accountSettings.getSyncInterval(SyncDataType.CONTACTS))
        assertEquals(2L, accountSettings.getSyncInterval(SyncDataType.EVENTS))
        assertEquals(3L, accountSettings.getSyncInterval(SyncDataType.TASKS))

        // test manual sync interval
        store.putValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS, "-1")
        assertNull(accountSettings.getSyncInterval(SyncDataType.CONTACTS))

        // test fallback to default setting
        every { settingsManager.getLongOrNull(Settings.DEFAULT_SYNC_INTERVAL) } returns 4L
        store.putValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS, null)
        assertEquals(4L, accountSettings.getSyncInterval(SyncDataType.CONTACTS))
    }

    @Test
    fun test_setSyncInterval() = runTest {
        accountSettings.setSyncInterval(SyncDataType.CONTACTS, 1)
        accountSettings.setSyncInterval(SyncDataType.EVENTS, 2)
        accountSettings.setSyncInterval(SyncDataType.TASKS, 3)

        assertEquals("1", store.getValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS))
        assertEquals("2", store.getValue(KEY_SYNC_INTERVAL_CALENDARS))
        assertEquals("3", store.getValue(KEY_SYNC_INTERVAL_TASKS))

        accountSettings.setSyncInterval(SyncDataType.CONTACTS, null)
        assertEquals("-1", store.getValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS))

        verify(exactly = 4) { automaticSyncManager.updateAutomaticSync(any(), any()) }
    }

    @Test
    fun test_getSyncWifiOnly() = runTest {
        every { settingsManager.containsKey(KEY_WIFI_ONLY) } returns true
        every { settingsManager.getBoolean(KEY_WIFI_ONLY) } returns true
        assertTrue(accountSettings.getSyncWifiOnly())

        every { settingsManager.getBoolean(KEY_WIFI_ONLY) } returns false
        assertFalse(accountSettings.getSyncWifiOnly())

        verify { settingsManager.containsKey(KEY_WIFI_ONLY) }
        verify { settingsManager.getBoolean(KEY_WIFI_ONLY) }

        every { settingsManager.containsKey(KEY_WIFI_ONLY) } returns false
        store.putValue(KEY_WIFI_ONLY, "true")
        assertTrue(accountSettings.getSyncWifiOnly())

        store.putValue(KEY_WIFI_ONLY, null)
        assertFalse(accountSettings.getSyncWifiOnly())
    }
}
