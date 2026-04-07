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
        AccountSettings.initialUserData(null, null).let { bundle ->
            assertEquals(AccountSettings.CURRENT_VERSION.toString(), bundle.getString(AccountSettings.KEY_SETTINGS_VERSION))

            // Credentials
            assertNull(bundle.getString(AccountSettings.KEY_USERNAME))
            assertNull(bundle.getString(AccountSettings.KEY_CERTIFICATE_ALIAS))
            assertNull(bundle.getString(AccountSettings.KEY_AUTH_STATE))

            // Preconfiguration URL
            assertNull(bundle.getString(AccountSettings.KEY_PRECONFIGURATION_URL))
        }

        // Pass credentials
        val credentials = Credentials("username", null, "alias", AuthState())
        AccountSettings.initialUserData(credentials, null).let { bundle ->
            assertEquals(AccountSettings.CURRENT_VERSION.toString(), bundle.getString(AccountSettings.KEY_SETTINGS_VERSION))

            // Credentials
            assertEquals("username", bundle.getString(AccountSettings.KEY_USERNAME))
            assertEquals("alias", bundle.getString(AccountSettings.KEY_CERTIFICATE_ALIAS))
            assertEquals("{}", bundle.getString(AccountSettings.KEY_AUTH_STATE))

            // Preconfiguration URL
            assertNull(bundle.getString(AccountSettings.KEY_PRECONFIGURATION_URL))
        }

        // Pass preconfiguration URL
        AccountSettings.initialUserData(null, "https://example.com").let { bundle ->
            assertEquals(AccountSettings.CURRENT_VERSION.toString(), bundle.getString(AccountSettings.KEY_SETTINGS_VERSION))

            // Credentials
            assertNull(bundle.getString(AccountSettings.KEY_USERNAME))
            assertNull(bundle.getString(AccountSettings.KEY_CERTIFICATE_ALIAS))
            assertNull(bundle.getString(AccountSettings.KEY_AUTH_STATE))

            // Preconfiguration URL
            assertEquals("https://example.com", bundle.getString(AccountSettings.KEY_PRECONFIGURATION_URL))
        }
    }

}