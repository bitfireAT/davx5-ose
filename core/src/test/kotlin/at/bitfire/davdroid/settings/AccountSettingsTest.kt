/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSettingsTest {
    private fun provideAccountSettings(
        storage: Map<String, String> = emptyMap(),
        sensitiveStorage: Map<String, SensitiveString> = emptyMap()
    ): Pair<AccountSettings, AccountSettingsStore> {
        val store = InMemorySettingsStore(storage, sensitiveStorage)
        return AccountSettings(
            accountId = LegacyAccount(Account("test", "test")),
            store = store,
            automaticSyncManager = mockk(relaxed = true),
            settingsManager = mockk(relaxed = true)
        ) to store
    }

    @Test
    fun test_credentials_get() {
        val (accountSettings) = provideAccountSettings(
            storage = mapOf(
                AccountSettings.KEY_USERNAME to "username",
                AccountSettings.KEY_CERTIFICATE_ALIAS to "certificateAlias",
            ),
            sensitiveStorage = mapOf(
                AccountSettings.KEY_PASSWORD to "password".toSensitiveString()
            )
        )
        val credentials = accountSettings.credentials()
        assertEquals(credentials.username, "username")
        assertEquals(credentials.password, "password".toSensitiveString())
        assertEquals(credentials.certificateAlias, "certificateAlias")
        assertNull(credentials.authState)
    }

    @Test
    fun test_credentials_set() {
        val (accountSettings, store) = provideAccountSettings()
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
}
