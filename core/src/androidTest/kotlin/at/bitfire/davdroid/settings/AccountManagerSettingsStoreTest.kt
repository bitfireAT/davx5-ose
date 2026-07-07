/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.sync.account.TestAccount
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
class AccountManagerSettingsStoreTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var accountSettingsFactory: AccountManagerSettingsStore.Factory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)


    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context)
    }


    @Test(expected = IllegalArgumentException::class)
    fun testUpdate_MissingMigrations() {
        TestAccount.provide(version = 1) { account ->
            // will run AccountManagerSettingsStore.update
            accountSettingsFactory.create(account, abortOnMissingMigration = true)
        }
    }

    @Test
    fun testUpdate_RunAllMigrations() {
        TestAccount.provide(version = 6) { account ->
            // will run AccountManagerSettingsStore.update
            accountSettingsFactory.create(account, abortOnMissingMigration = true)

            val accountManager = AccountManager.get(context)
            val version = accountManager.getUserData(account, AccountManagerSettingsStore.KEY_SETTINGS_VERSION).toInt()
            assertEquals(AccountManagerSettingsStore.CURRENT_VERSION, version)
        }
    }

    @Test
    fun test_initialUserData() {
        AccountManagerSettingsStore.initialUserData(null, null).let { userData ->
            assertEquals(AccountManagerSettingsStore.CURRENT_VERSION.toString(), userData[AccountManagerSettingsStore.KEY_SETTINGS_VERSION])

            // Credentials
            assertNull(userData[AccountManagerSettingsStore.KEY_USERNAME])
            assertNull(userData[AccountManagerSettingsStore.KEY_CERTIFICATE_ALIAS])
            assertNull(userData[AccountManagerSettingsStore.KEY_AUTH_STATE])

            // Preconfiguration URL
            assertNull(userData[AccountManagerSettingsStore.KEY_PRECONFIGURATION_URL])
        }
    }

    @Test
    fun test_initialUserData_credentials() {
        val credentials = Credentials("username", null, "alias", AuthState())
        AccountManagerSettingsStore.initialUserData(credentials, null).let { userData ->
            assertEquals(AccountManagerSettingsStore.CURRENT_VERSION.toString(), userData[AccountManagerSettingsStore.KEY_SETTINGS_VERSION])

            // Credentials
            assertEquals("username", userData[AccountManagerSettingsStore.KEY_USERNAME])
            assertEquals("alias", userData[AccountManagerSettingsStore.KEY_CERTIFICATE_ALIAS])
            assertEquals("{}", userData[AccountManagerSettingsStore.KEY_AUTH_STATE])

            // Preconfiguration URL
            assertNull(userData[AccountManagerSettingsStore.KEY_PRECONFIGURATION_URL])
        }
    }

    @Test
    fun test_initialUserData_preconfigurationUrl() {
        AccountManagerSettingsStore.initialUserData(null, "https://example.com").let { userData ->
            assertEquals(AccountManagerSettingsStore.CURRENT_VERSION.toString(), userData[AccountManagerSettingsStore.KEY_SETTINGS_VERSION])

            // Credentials
            assertNull(userData[AccountManagerSettingsStore.KEY_USERNAME])
            assertNull(userData[AccountManagerSettingsStore.KEY_CERTIFICATE_ALIAS])
            assertNull(userData[AccountManagerSettingsStore.KEY_AUTH_STATE])

            // Preconfiguration URL
            assertEquals("https://example.com", userData[AccountManagerSettingsStore.KEY_PRECONFIGURATION_URL])
        }
    }

}
