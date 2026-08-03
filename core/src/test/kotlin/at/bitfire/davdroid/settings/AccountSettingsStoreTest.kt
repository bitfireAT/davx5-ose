/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.CREDENTIALS_LOCK
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.DEFAULT_TIME_RANGE_PAST_DAYS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_AUTH_STATE
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_CERTIFICATE_ALIAS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_CONTACT_GROUP_METHOD
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_DEFAULT_ALARM
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_EVENT_COLORS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_IGNORE_VPNS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_MANAGE_CALENDAR_COLORS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_PASSWORD
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_SHOW_ONLY_PERSONAL
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_SYNC_INTERVAL_ADDRESSBOOKS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_TIME_RANGE_PAST_DAYS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_USERNAME
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_WIFI_ONLY
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_WIFI_ONLY_SSIDS
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.SYNC_INTERVAL_MANUALLY
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.vcard.GroupMethod
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.openid.appauth.AuthState
import org.junit.Test

class AccountSettingsStoreTest {

    @Test
    fun test_credentials_get() {
        val store = mockk<AccountSettingsStore> {}
        every { store.get(KEY_USERNAME) } returns "username"
        every { store.getSensitiveValue(KEY_PASSWORD) } returns "password".toSensitiveString()
        every { store.get(KEY_CERTIFICATE_ALIAS) } returns "certificateAlias"
        every { store.get(KEY_AUTH_STATE) } returns null
        every { store.credentials() } answers { callOriginal() }

        val credentials = store.credentials()
        assert(credentials.username == "username")
        assert(credentials.password == "password".toSensitiveString())
        assert(credentials.certificateAlias == "certificateAlias")
        assert(credentials.authState == null)
    }

    @Test
    fun test_credentials_set() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setSensitiveValue(any(), any()) } answers { }
        every { store.credentials(any()) } answers { callOriginal() }
        every { store.updateAuthState(any()) } answers { }

        store.credentials(
            Credentials(
                username = "username",
                password = "password".toSensitiveString(),
                certificateAlias = "certificateAlias",
                authState = mockk()
            )
        )

        verify { store.set(KEY_USERNAME, "username") }
        verify { store.setSensitiveValue(KEY_PASSWORD, "password".toSensitiveString()) }
        verify { store.set(KEY_CERTIFICATE_ALIAS, "certificateAlias") }
        verify { store.updateAuthState(any()) }
    }

    @Test
    fun test_updateUpdateAuthState() {
        val store = mockk<AccountSettingsStore>()
        val authState = mockk<AuthState>()
        every { store.updateAuthState(any()) } answers { callOriginal() }
        every { store.set(KEY_AUTH_STATE, any()) } answers { }
        every { authState.jsonSerializeString() } returns "authStateJson"

        store.updateAuthState(authState)

        verify { store.set(KEY_AUTH_STATE, "authStateJson") }
    }

    @Test
    fun test_changingCredentialsAllowed_noLock() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(CREDENTIALS_LOCK) } returns null
        every { store.changingCredentialsAllowed() } answers { callOriginal() }

        assert(store.changingCredentialsAllowed())
    }

    @Test
    fun test_changingCredentialsAllowed_lockedAtLoginAndSettings() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(CREDENTIALS_LOCK) } returns CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
        every { store.changingCredentialsAllowed() } answers { callOriginal() }

        assert(!store.changingCredentialsAllowed())
    }

    @Test
    fun test_getSyncInterval_storedValue() {
        val store = mockk<AccountSettingsStore>()
        every { store.get(KEY_SYNC_INTERVAL_ADDRESSBOOKS) } returns "3600"
        every { store.getSyncInterval(SyncDataType.CONTACTS) } answers { callOriginal() }

        assert(store.getSyncInterval(SyncDataType.CONTACTS) == 3600L)
    }

    @Test
    fun test_getSyncInterval_manualSync() {
        val store = mockk<AccountSettingsStore>()
        every { store.get(KEY_SYNC_INTERVAL_ADDRESSBOOKS) } returns SYNC_INTERVAL_MANUALLY.toString()
        every { store.getSyncInterval(SyncDataType.CONTACTS) } answers { callOriginal() }

        assert(store.getSyncInterval(SyncDataType.CONTACTS) == null)
    }

    @Test
    fun test_getSyncInterval_defaultFromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.get(KEY_SYNC_INTERVAL_ADDRESSBOOKS) } returns null
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getLongOrNull(Settings.DEFAULT_SYNC_INTERVAL) } returns 900L
        every { store.getSyncInterval(SyncDataType.CONTACTS) } answers { callOriginal() }

        assert(store.getSyncInterval(SyncDataType.CONTACTS) == 900L)
    }

    @Test
    fun test_setSyncInterval_seconds() {
        val store = mockk<AccountSettingsStore>()
        val automaticSyncManager = mockk<AutomaticSyncManager>()
        val accountId = mockk<AccountId>()
        every { store.accountId } returns accountId
        every { store.automaticSyncManager } returns automaticSyncManager
        every { store.set(any(), any()) } answers { }
        every { automaticSyncManager.updateAutomaticSync(any<AccountId>(), any<SyncDataType>()) } answers { }
        every { store.setSyncInterval(any(), any()) } answers { callOriginal() }

        store.setSyncInterval(SyncDataType.CONTACTS, 3600L)

        verify { store.set(KEY_SYNC_INTERVAL_ADDRESSBOOKS, "3600") }
        verify { automaticSyncManager.updateAutomaticSync(accountId, SyncDataType.CONTACTS) }
    }

    @Test
    fun test_setSyncInterval_null() {
        val store = mockk<AccountSettingsStore>()
        val automaticSyncManager = mockk<AutomaticSyncManager>()
        val accountId = mockk<AccountId>()
        every { store.accountId } returns accountId
        every { store.automaticSyncManager } returns automaticSyncManager
        every { store.set(any(), any()) } answers { }
        every { automaticSyncManager.updateAutomaticSync(any<AccountId>(), any<SyncDataType>()) } answers { }
        every { store.setSyncInterval(any(), any()) } answers { callOriginal() }

        store.setSyncInterval(SyncDataType.CONTACTS, null)

        verify { store.set(KEY_SYNC_INTERVAL_ADDRESSBOOKS, SYNC_INTERVAL_MANUALLY.toString()) }
    }

    @Test
    fun test_getSyncWifiOnly_fromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_WIFI_ONLY) } returns true
        every { settingsManager.getBoolean(KEY_WIFI_ONLY) } returns true
        every { store.getSyncWifiOnly() } answers { callOriginal() }

        assert(store.getSyncWifiOnly())
    }

    @Test
    fun test_getSyncWifiOnly_fromLocalSetting() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_WIFI_ONLY) } returns false
        every { store.get(KEY_WIFI_ONLY) } returns "1"
        every { store.getSyncWifiOnly() } answers { callOriginal() }

        assert(store.getSyncWifiOnly())
    }

    @Test
    fun test_setSyncWiFiOnly() {
        val store = mockk<AccountSettingsStore>()
        val automaticSyncManager = mockk<AutomaticSyncManager>()
        val accountId = mockk<AccountId>()
        every { store.accountId } returns accountId
        every { store.automaticSyncManager } returns automaticSyncManager
        every { store.set(any(), any()) } answers { }
        every { automaticSyncManager.updateAutomaticSync(any<AccountId>()) } answers { }
        every { store.setSyncWiFiOnly(any()) } answers { callOriginal() }

        store.setSyncWiFiOnly(true)

        verify { store.set(KEY_WIFI_ONLY, "1") }
        verify { automaticSyncManager.updateAutomaticSync(accountId) }
    }

    @Test
    fun test_getSyncWifiOnlySSIDs_wifiOnlyDisabled() {
        val store = mockk<AccountSettingsStore>()
        every { store.getSyncWifiOnly() } returns false
        every { store.getSyncWifiOnlySSIDs() } answers { callOriginal() }

        assert(store.getSyncWifiOnlySSIDs() == null)
    }

    @Test
    fun test_getSyncWifiOnlySSIDs_fromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.getSyncWifiOnly() } returns true
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_WIFI_ONLY_SSIDS) } returns true
        every { settingsManager.getString(KEY_WIFI_ONLY_SSIDS) } returns "SSID1,SSID2"
        every { store.getSyncWifiOnlySSIDs() } answers { callOriginal() }

        assert(store.getSyncWifiOnlySSIDs() == listOf("SSID1", "SSID2"))
    }

    @Test
    fun test_setSyncWifiOnlySSIDs() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setSyncWifiOnlySSIDs(any()) } answers { callOriginal() }

        store.setSyncWifiOnlySSIDs(listOf("SSID1", "SSID2"))

        verify { store.set(KEY_WIFI_ONLY_SSIDS, "SSID1,SSID2") }
    }

    @Test
    fun test_getIgnoreVpns_fromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.get(KEY_IGNORE_VPNS) } returns null
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getBoolean(KEY_IGNORE_VPNS) } returns true
        every { store.getIgnoreVpns() } answers { callOriginal() }

        assert(store.getIgnoreVpns())
    }

    @Test
    fun test_getIgnoreVpns_explicitlyDisabled() {
        val store = mockk<AccountSettingsStore>()
        every { store.get(KEY_IGNORE_VPNS) } returns "0"
        every { store.getIgnoreVpns() } answers { callOriginal() }

        assert(!store.getIgnoreVpns())
    }

    @Test
    fun test_setIgnoreVpns() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setIgnoreVpns(any()) } answers { callOriginal() }

        store.setIgnoreVpns(true)

        verify { store.set(KEY_IGNORE_VPNS, "1") }
    }

    @Test
    fun test_getTimeRangePastDays_default() {
        val store = mockk<AccountSettingsStore>()
        every { store.get(KEY_TIME_RANGE_PAST_DAYS) } returns null
        every { store.getTimeRangePastDays() } answers { callOriginal() }

        assert(store.getTimeRangePastDays() == DEFAULT_TIME_RANGE_PAST_DAYS)
    }

    @Test
    fun test_getTimeRangePastDays_noLimit() {
        val store = mockk<AccountSettingsStore>()
        every { store.get(KEY_TIME_RANGE_PAST_DAYS) } returns "-1"
        every { store.getTimeRangePastDays() } answers { callOriginal() }

        assert(store.getTimeRangePastDays() == null)
    }

    @Test
    fun test_setTimeRangePastDays() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setTimeRangePastDays(any()) } answers { callOriginal() }

        store.setTimeRangePastDays(null)

        verify { store.set(KEY_TIME_RANGE_PAST_DAYS, "-1") }
    }

    @Test
    fun test_getDefaultAlarm_fromLocalSetting() {
        val store = mockk<AccountSettingsStore>()
        every { store.get(KEY_DEFAULT_ALARM) } returns "30"
        every { store.getDefaultAlarm() } answers { callOriginal() }

        assert(store.getDefaultAlarm() == 30)
    }

    @Test
    fun test_getDefaultAlarm_disabledInSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.get(KEY_DEFAULT_ALARM) } returns null
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(KEY_DEFAULT_ALARM) } returns -1
        every { store.getDefaultAlarm() } answers { callOriginal() }

        assert(store.getDefaultAlarm() == null)
    }

    @Test
    fun test_setDefaultAlarm_sameAsSettingsManager_removesLocalSetting() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(KEY_DEFAULT_ALARM) } returns 30
        every { store.set(any(), any()) } answers { }
        every { store.setDefaultAlarm(any()) } answers { callOriginal() }

        store.setDefaultAlarm(30)

        verify { store.set(KEY_DEFAULT_ALARM, null) }
    }

    @Test
    fun test_getManageCalendarColors_fromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_MANAGE_CALENDAR_COLORS) } returns true
        every { settingsManager.getBoolean(KEY_MANAGE_CALENDAR_COLORS) } returns false
        every { store.getManageCalendarColors() } answers { callOriginal() }

        assert(!store.getManageCalendarColors())
    }

    @Test
    fun test_getManageCalendarColors_defaultTrue() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_MANAGE_CALENDAR_COLORS) } returns false
        every { store.get(KEY_MANAGE_CALENDAR_COLORS) } returns null
        every { store.getManageCalendarColors() } answers { callOriginal() }

        assert(store.getManageCalendarColors())
    }

    @Test
    fun test_setManageCalendarColors() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setManageCalendarColors(any()) } answers { callOriginal() }

        store.setManageCalendarColors(false)

        verify { store.set(KEY_MANAGE_CALENDAR_COLORS, "0") }
    }

    @Test
    fun test_getEventColors_fromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_EVENT_COLORS) } returns true
        every { settingsManager.getBoolean(KEY_EVENT_COLORS) } returns true
        every { store.getEventColors() } answers { callOriginal() }

        assert(store.getEventColors())
    }

    @Test
    fun test_getEventColors_defaultFalse() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.containsKey(KEY_EVENT_COLORS) } returns false
        every { store.get(KEY_EVENT_COLORS) } returns null
        every { store.getEventColors() } answers { callOriginal() }

        assert(!store.getEventColors())
    }

    @Test
    fun test_setEventColors() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setEventColors(any()) } answers { callOriginal() }

        store.setEventColors(true)

        verify { store.set(KEY_EVENT_COLORS, "1") }
    }

    @Test
    fun test_getGroupMethod_fromSettingsManager() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getString(KEY_CONTACT_GROUP_METHOD) } returns "CATEGORIES"
        every { store.getGroupMethod() } answers { callOriginal() }

        assert(store.getGroupMethod() == GroupMethod.CATEGORIES)
    }

    @Test
    fun test_getGroupMethod_defaultWhenInvalid() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getString(KEY_CONTACT_GROUP_METHOD) } returns null
        every { store.get(KEY_CONTACT_GROUP_METHOD) } returns "INVALID"
        every { store.getGroupMethod() } answers { callOriginal() }

        assert(store.getGroupMethod() == GroupMethod.GROUP_VCARDS)
    }

    @Test
    fun test_setGroupMethod() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setGroupMethod(any()) } answers { callOriginal() }

        store.setGroupMethod(GroupMethod.CATEGORIES)

        verify { store.set(KEY_CONTACT_GROUP_METHOD, "CATEGORIES") }
    }

    @Test
    fun test_getShowOnlyPersonal_lockedTrue() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns 1
        every { store.getShowOnlyPersonal() } answers { callOriginal() }

        assert(store.getShowOnlyPersonal())
    }

    @Test
    fun test_getShowOnlyPersonal_fromLocalSetting() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns null
        every { store.get(KEY_SHOW_ONLY_PERSONAL) } returns "1"
        every { store.getShowOnlyPersonal() } answers { callOriginal() }

        assert(store.getShowOnlyPersonal())
    }

    @Test
    fun test_getShowOnlyPersonalLocked_true() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns 0
        every { store.getShowOnlyPersonalLocked() } answers { callOriginal() }

        assert(store.getShowOnlyPersonalLocked())
    }

    @Test
    fun test_getShowOnlyPersonalLocked_false() {
        val store = mockk<AccountSettingsStore>()
        val settingsManager = mockk<SettingsManager>()
        every { store.settingsManager } returns settingsManager
        every { settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL) } returns null
        every { store.getShowOnlyPersonalLocked() } answers { callOriginal() }

        assert(!store.getShowOnlyPersonalLocked())
    }

    @Test
    fun test_setShowOnlyPersonal() {
        val store = mockk<AccountSettingsStore>()
        every { store.set(any(), any()) } answers { }
        every { store.setShowOnlyPersonal(any()) } answers { callOriginal() }

        store.setShowOnlyPersonal(false)

        verify { store.set(KEY_SHOW_ONLY_PERSONAL, null) }
    }

}
