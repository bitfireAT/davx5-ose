/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.settings.AccountSettings.Companion.CREDENTIALS_LOCK
import at.bitfire.davdroid.settings.AccountSettings.Companion.CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_CONTACT_GROUP_METHOD
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_DEFAULT_ALARM
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_EVENT_COLORS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_IGNORE_VPNS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_MANAGE_CALENDAR_COLORS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SHOW_ONLY_PERSONAL
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SYNC_INTERVAL_ADDRESSBOOKS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SYNC_INTERVAL_CALENDARS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SYNC_INTERVAL_TASKS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_TIME_RANGE_PAST_DAYS
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_WIFI_ONLY
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_WIFI_ONLY_SSIDS
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.vcard.GroupMethod
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.openid.appauth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsTest {

    private val store = InMemorySettingsStore()
    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val automaticSyncManager = mockk<AutomaticSyncManager>(relaxed = true)
    private val accountSettings = AccountSettings(
        accountId = LegacyAccount(Account("test", "test")),
        store = store,
        automaticSyncManager = automaticSyncManager,
        settingsManager = settingsManager
    )

    @Test
    fun test_credentials_get() {
        store.putValue(AccountSettings.KEY_USERNAME, "username")
        store.putValue(AccountSettings.KEY_CERTIFICATE_ALIAS, "certificateAlias")
        store.putSensitiveValue(AccountSettings.KEY_PASSWORD, "password".toSensitiveString())

        val credentials = accountSettings.credentials()

        assertEquals(credentials.username, "username")
        assertEquals(credentials.password, "password".toSensitiveString())
        assertEquals(credentials.certificateAlias, "certificateAlias")
        assertNull(credentials.authState)
    }

    @Test
    fun test_credentials_set() {
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
    fun test_updateAuthState() {
        // Validate it stores the serialized auth state
        val authState = mockk<AuthState> {
            every { jsonSerializeString() } returns "serialized-auth-state"
        }
        accountSettings.updateAuthState(authState)
        assertEquals("serialized-auth-state", store.getValue(AccountSettings.KEY_AUTH_STATE))
    }

    @Test
    fun test_changingCredentialsAllowed() {
        // Mock the value, verify it works correctly
        every { settingsManager.getIntOrNull(CREDENTIALS_LOCK) } returns 0
        assertTrue(accountSettings.changingCredentialsAllowed())
        verify { settingsManager.getIntOrNull(CREDENTIALS_LOCK) }
    }

    @Test
    fun test_changingCredentialsAllowed_not() {
        // Mock the value, verify it works correctly
        every { settingsManager.getIntOrNull(CREDENTIALS_LOCK) } returns CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
        assertFalse(accountSettings.changingCredentialsAllowed())
        verify { settingsManager.getIntOrNull(CREDENTIALS_LOCK) }
    }

    @Test
    fun test_getSyncInterval() {
        store.putValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS, "1")
        store.putValue(KEY_SYNC_INTERVAL_CALENDARS, "2")
        store.putValue(KEY_SYNC_INTERVAL_TASKS, "3")

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
    fun test_setSyncInterval() {
        // Set sync intervals for all data types
        accountSettings.setSyncInterval(SyncDataType.CONTACTS, 1)
        accountSettings.setSyncInterval(SyncDataType.EVENTS, 2)
        accountSettings.setSyncInterval(SyncDataType.TASKS, 3)

        // Verify they've been stored correctly
        assertEquals("1", store.getValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS))
        assertEquals("2", store.getValue(KEY_SYNC_INTERVAL_CALENDARS))
        assertEquals("3", store.getValue(KEY_SYNC_INTERVAL_TASKS))

        // Set address books to null (manually), and verify it's set correctly
        accountSettings.setSyncInterval(SyncDataType.CONTACTS, null)
        assertEquals("-1", store.getValue(KEY_SYNC_INTERVAL_ADDRESSBOOKS))

        verify(exactly = 4) { automaticSyncManager.updateAutomaticSync(any(), any()) }
    }

    @Test
    fun test_getSyncWifiOnly() {
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

    @Test
    fun test_setSyncWiFiOnly() {
        accountSettings.setSyncWiFiOnly(true)
        assertEquals("1", store.getValue(KEY_WIFI_ONLY))
        verify { automaticSyncManager.updateAutomaticSync(any()) }

        accountSettings.setSyncWiFiOnly(false)
        assertNull(store.getValue(KEY_WIFI_ONLY))
    }

    @Test
    fun test_getSyncWifiOnlySSIDs() {
        // WiFi-only sync disabled -> no SSID restriction
        every { settingsManager.containsKey(KEY_WIFI_ONLY) } returns false
        assertNull(accountSettings.getSyncWifiOnlySSIDs())

        // WiFi-only sync enabled (locally), but no SSIDs configured
        store.putValue(KEY_WIFI_ONLY, "1")
        assertNull(accountSettings.getSyncWifiOnlySSIDs())

        // SSIDs stored locally
        every { settingsManager.containsKey(KEY_WIFI_ONLY_SSIDS) } returns false
        store.putValue(KEY_WIFI_ONLY_SSIDS, "ssid1,ssid2")
        assertEquals(listOf("ssid1", "ssid2"), accountSettings.getSyncWifiOnlySSIDs())

        // SSIDs provided by settings provider take precedence
        every { settingsManager.containsKey(KEY_WIFI_ONLY_SSIDS) } returns true
        every { settingsManager.getString(KEY_WIFI_ONLY_SSIDS) } returns "ssid3"
        assertEquals(listOf("ssid3"), accountSettings.getSyncWifiOnlySSIDs())
    }

    @Test
    fun test_setSyncWifiOnlySSIDs() {
        accountSettings.setSyncWifiOnlySSIDs(listOf("ssid1", "ssid2"))
        assertEquals("ssid1,ssid2", store.getValue(KEY_WIFI_ONLY_SSIDS))

        // null and empty list both clear the setting
        accountSettings.setSyncWifiOnlySSIDs(null)
        assertNull(store.getValue(KEY_WIFI_ONLY_SSIDS))

        accountSettings.setSyncWifiOnlySSIDs(emptyList())
        assertNull(store.getValue(KEY_WIFI_ONLY_SSIDS))
    }

    @Test
    fun test_getIgnoreVpns() {
        // no local setting -> fall back to settings provider
        every { settingsManager.getBoolean(KEY_IGNORE_VPNS) } returns true
        assertTrue(accountSettings.getIgnoreVpns())

        every { settingsManager.getBoolean(KEY_IGNORE_VPNS) } returns false
        assertFalse(accountSettings.getIgnoreVpns())

        // local setting "0" always means false
        store.putValue(KEY_IGNORE_VPNS, "0")
        assertFalse(accountSettings.getIgnoreVpns())

        // any other local value means true
        store.putValue(KEY_IGNORE_VPNS, "1")
        assertTrue(accountSettings.getIgnoreVpns())
    }

    @Test
    fun test_setIgnoreVpns() {
        accountSettings.setIgnoreVpns(true)
        assertEquals("1", store.getValue(KEY_IGNORE_VPNS))

        accountSettings.setIgnoreVpns(false)
        assertEquals("0", store.getValue(KEY_IGNORE_VPNS))
    }

    @Test
    fun test_getTimeRangePastDays() {
        // no value stored -> default
        assertEquals(AccountSettings.DEFAULT_TIME_RANGE_PAST_DAYS, accountSettings.getTimeRangePastDays())

        // negative value -> no limit
        store.putValue(KEY_TIME_RANGE_PAST_DAYS, "-1")
        assertNull(accountSettings.getTimeRangePastDays())

        // positive value -> limit
        store.putValue(KEY_TIME_RANGE_PAST_DAYS, "30")
        assertEquals(30, accountSettings.getTimeRangePastDays())
    }

    @Test
    fun test_setTimeRangePastDays() {
        accountSettings.setTimeRangePastDays(30)
        assertEquals("30", store.getValue(KEY_TIME_RANGE_PAST_DAYS))

        accountSettings.setTimeRangePastDays(null)
        assertEquals("-1", store.getValue(KEY_TIME_RANGE_PAST_DAYS))
    }

    @Test
    fun test_getDefaultAlarm() {
        // no local setting -> fall back to settings provider
        every { settingsManager.getIntOrNull(KEY_DEFAULT_ALARM) } returns 15
        assertEquals(15, accountSettings.getDefaultAlarm())

        // settings provider value of -1 means "no default alarm"
        every { settingsManager.getIntOrNull(KEY_DEFAULT_ALARM) } returns -1
        assertNull(accountSettings.getDefaultAlarm())

        // local setting takes precedence over settings provider
        store.putValue(KEY_DEFAULT_ALARM, "30")
        assertEquals(30, accountSettings.getDefaultAlarm())
    }

    @Test
    fun test_setDefaultAlarm() {
        every { settingsManager.getIntOrNull(KEY_DEFAULT_ALARM) } returns 15

        // new value differs from settings provider value -> store locally
        accountSettings.setDefaultAlarm(30)
        assertEquals("30", store.getValue(KEY_DEFAULT_ALARM))

        // new value equals settings provider value -> remove local setting
        accountSettings.setDefaultAlarm(15)
        assertNull(store.getValue(KEY_DEFAULT_ALARM))
    }

    @Test
    fun test_getManageCalendarColors() {
        every { settingsManager.containsKey(KEY_MANAGE_CALENDAR_COLORS) } returns true
        every { settingsManager.getBoolean(KEY_MANAGE_CALENDAR_COLORS) } returns true
        assertTrue(accountSettings.getManageCalendarColors())

        every { settingsManager.getBoolean(KEY_MANAGE_CALENDAR_COLORS) } returns false
        assertFalse(accountSettings.getManageCalendarColors())

        // no settings provider value -> fall back to local setting (default: true)
        every { settingsManager.containsKey(KEY_MANAGE_CALENDAR_COLORS) } returns false
        assertTrue(accountSettings.getManageCalendarColors())

        store.putValue(KEY_MANAGE_CALENDAR_COLORS, "0")
        assertFalse(accountSettings.getManageCalendarColors())
    }

    @Test
    fun test_setManageCalendarColors() {
        accountSettings.setManageCalendarColors(true)
        assertNull(store.getValue(KEY_MANAGE_CALENDAR_COLORS))

        accountSettings.setManageCalendarColors(false)
        assertEquals("0", store.getValue(KEY_MANAGE_CALENDAR_COLORS))
    }

    @Test
    fun test_getEventColors() {
        every { settingsManager.containsKey(KEY_EVENT_COLORS) } returns true
        every { settingsManager.getBoolean(KEY_EVENT_COLORS) } returns true
        assertTrue(accountSettings.getEventColors())

        every { settingsManager.getBoolean(KEY_EVENT_COLORS) } returns false
        assertFalse(accountSettings.getEventColors())

        // no settings provider value -> fall back to local setting (default: false)
        every { settingsManager.containsKey(KEY_EVENT_COLORS) } returns false
        assertFalse(accountSettings.getEventColors())

        store.putValue(KEY_EVENT_COLORS, "1")
        assertTrue(accountSettings.getEventColors())
    }

    @Test
    fun test_setEventColors() {
        accountSettings.setEventColors(true)
        assertEquals("1", store.getValue(KEY_EVENT_COLORS))

        accountSettings.setEventColors(false)
        assertNull(store.getValue(KEY_EVENT_COLORS))
    }

    @Test
    fun test_getGroupMethod() {
        // no value at all -> default
        every { settingsManager.getString(KEY_CONTACT_GROUP_METHOD) } returns null
        assertEquals(GroupMethod.GROUP_VCARDS, accountSettings.getGroupMethod())

        // local setting
        store.putValue(KEY_CONTACT_GROUP_METHOD, "CATEGORIES")
        assertEquals(GroupMethod.CATEGORIES, accountSettings.getGroupMethod())

        // settings provider takes precedence over local setting
        every { settingsManager.getString(KEY_CONTACT_GROUP_METHOD) } returns "GROUP_VCARDS"
        assertEquals(GroupMethod.GROUP_VCARDS, accountSettings.getGroupMethod())

        // invalid value -> default
        every { settingsManager.getString(KEY_CONTACT_GROUP_METHOD) } returns "INVALID"
        assertEquals(GroupMethod.GROUP_VCARDS, accountSettings.getGroupMethod())
    }

    @Test
    fun test_setGroupMethod() {
        accountSettings.setGroupMethod(GroupMethod.CATEGORIES)
        assertEquals("CATEGORIES", store.getValue(KEY_CONTACT_GROUP_METHOD))
    }

    @Test
    fun test_getShowOnlyPersonal() {
        // settings provider value 0 -> false, regardless of local setting
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns 0
        store.putValue(KEY_SHOW_ONLY_PERSONAL, "1")
        assertFalse(accountSettings.getShowOnlyPersonal())

        // settings provider value 1 -> true
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns 1
        assertTrue(accountSettings.getShowOnlyPersonal())

        // no settings provider value -> fall back to local setting
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns null
        assertTrue(accountSettings.getShowOnlyPersonal())

        store.putValue(KEY_SHOW_ONLY_PERSONAL, null)
        assertFalse(accountSettings.getShowOnlyPersonal())
    }

    @Test
    fun test_getShowOnlyPersonalLocked() {
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns 0
        assertTrue(accountSettings.getShowOnlyPersonalLocked())

        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns 1
        assertTrue(accountSettings.getShowOnlyPersonalLocked())

        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns null
        assertFalse(accountSettings.getShowOnlyPersonalLocked())
    }

    @Test
    fun test_setShowOnlyPersonal() {
        accountSettings.setShowOnlyPersonal(true)
        assertEquals("1", store.getValue(KEY_SHOW_ONLY_PERSONAL))

        accountSettings.setShowOnlyPersonal(false)
        assertNull(store.getValue(KEY_SHOW_ONLY_PERSONAL))
    }
}
