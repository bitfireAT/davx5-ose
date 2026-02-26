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
import org.junit.Assert.assertEquals
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
    fun test_initialUserData_meta() {
        val bundle = AccountSettings.initialUserData(null, mapOf("key1" to "value1", "key2" to "value2"))
        assertEquals("value1", bundle.getString("key1"))
        assertEquals("value2", bundle.getString("key2"))
    }

}