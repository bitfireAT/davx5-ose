/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.settings.AccountSettingsStore.Companion.KEY_PASSWORD
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.openid.appauth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)


    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context)
    }


    @Test
    fun test_get() {
        TestAccount.provide { account ->
            val store = accountSettingsFactory.create(account)
            store.accountManager.setAndVerifyUserData(account, "key", "value")
            assertEquals(
                "value",
                store.get("key")
            )
            assertNull(store.get("does-not-exist"))
        }
    }

    @Test
    fun test_getSensitiveValue() {
        TestAccount.provide { account ->
            val store = accountSettingsFactory.create(account)
            store.accountManager.setAndVerifyUserData(account, "key", "value")
            assertEquals(
                "value".toSensitiveString(),
                store.getSensitiveValue("key")
            )
            assertNull(store.getSensitiveValue("does-not-exist"))
        }
    }

    @Test
    fun test_set() {
        TestAccount.provide { account ->
            val store = accountSettingsFactory.create(account)
            store.accountManager.setAndVerifyUserData(account, "key", "value")

            store.set("key", "value2")
            assertEquals(
                "value2",
                store.accountManager.getUserData(account, "key")
            )

            store.set("key", null)
            assertNull(store.accountManager.getUserData(account, "key"))
        }
    }

    @Test
    fun test_set_password() {
        TestAccount.provide { account ->
            val store = accountSettingsFactory.create(account)
            store.accountManager.setAndVerifyUserData(account, KEY_PASSWORD, "password")
            store.accountManager.setPassword(account, "password")

            // Setting value with KEY_PASSWORD should update the user data, not the password
            store.set(KEY_PASSWORD, "new-password")
            assertEquals(
                "password",
                store.accountManager.getPassword(account)
            )
            assertEquals(
                "new-password",
                store.accountManager.getUserData(account, KEY_PASSWORD)
            )
        }
    }

    @Test
    fun test_setSensitiveValue() {
        TestAccount.provide { account ->
            val store = accountSettingsFactory.create(account)
            store.accountManager.setAndVerifyUserData(account, "key", "value")

            store.setSensitiveValue("key", "value2".toSensitiveString())
            assertEquals(
                "value2",
                store.accountManager.getUserData(account, "key")
            )

            store.setSensitiveValue("key", null)
            assertNull(store.accountManager.getUserData(account, "key"))
        }
    }

    @Test
    fun test_setSensitiveValue_password() {
        TestAccount.provide { account ->
            val store = accountSettingsFactory.create(account)
            store.accountManager.setAndVerifyUserData(account, KEY_PASSWORD, "password")
            store.accountManager.setPassword(account, "password")

            // Setting value with KEY_PASSWORD should update the password, not the user data
            store.setSensitiveValue(KEY_PASSWORD, "new-password".toSensitiveString())
            assertEquals(
                "new-password",
                store.accountManager.getPassword(account)
            )
            assertEquals(
                "password",
                store.accountManager.getUserData(account, KEY_PASSWORD)
            )
        }
    }


    @Test(expected = IllegalArgumentException::class)
    fun testUpdate_MissingMigrations() {
        TestAccount.provide(version = 1) { account ->
            // will run AccountSettings.update
            accountSettingsFactory.create(account, abortOnMissingMigration = true)
        }
    }

    @Test
    fun testUpdate_RunAllMigrations() {
        TestAccount.provide(version = 6) { account ->
            // will run AccountSettings.update
            accountSettingsFactory.create(account, abortOnMissingMigration = true)

            val accountManager = AccountManager.get(context)
            val version = accountManager.getUserData(account, AccountSettings.KEY_SETTINGS_VERSION).toInt()
            assertEquals(AccountSettings.CURRENT_VERSION, version)
        }
    }

    @Test
    fun test_initialUserData() {
        AccountSettings.initialUserData(null, null).let { userData ->
            assertEquals(AccountSettings.CURRENT_VERSION.toString(), userData[AccountSettings.KEY_SETTINGS_VERSION])

            // Credentials
            assertNull(userData[AccountSettingsStore.KEY_USERNAME])
            assertNull(userData[AccountSettingsStore.KEY_CERTIFICATE_ALIAS])
            assertNull(userData[AccountSettingsStore.KEY_AUTH_STATE])

            // Preconfiguration URL
            assertNull(userData[AccountSettingsStore.KEY_PRECONFIGURATION_URL])
        }
    }

    @Test
    fun test_initialUserData_credentials() {
        val credentials = Credentials("username", null, "alias", AuthState())
        AccountSettings.initialUserData(credentials, null).let { userData ->
            assertEquals(AccountSettings.CURRENT_VERSION.toString(), userData[AccountSettings.KEY_SETTINGS_VERSION])

            // Credentials
            assertEquals("username", userData[AccountSettingsStore.KEY_USERNAME])
            assertEquals("alias", userData[AccountSettingsStore.KEY_CERTIFICATE_ALIAS])
            assertEquals("{}", userData[AccountSettingsStore.KEY_AUTH_STATE])

            // Preconfiguration URL
            assertNull(userData[AccountSettingsStore.KEY_PRECONFIGURATION_URL])
        }
    }

    @Test
    fun test_initialUserData_preconfigurationUrl() {
        AccountSettings.initialUserData(null, "https://example.com").let { userData ->
            assertEquals(AccountSettings.CURRENT_VERSION.toString(), userData[AccountSettings.KEY_SETTINGS_VERSION])

            // Credentials
            assertNull(userData[AccountSettingsStore.KEY_USERNAME])
            assertNull(userData[AccountSettingsStore.KEY_CERTIFICATE_ALIAS])
            assertNull(userData[AccountSettingsStore.KEY_AUTH_STATE])

            // Preconfiguration URL
            assertEquals("https://example.com", userData[AccountSettingsStore.KEY_PRECONFIGURATION_URL])
        }
    }

}
