/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsTest {

    @Inject
    @ApplicationContext lateinit var context: Context

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    lateinit var testAccount: Account


    @Before
    fun setUp() {
        hiltRule.inject()
        testAccount = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(testAccount)
    }


    @Test
    fun testUpdate_RunAllMigrations() {
        val accountManager = AccountManager.get(context)
        val fromVersion = 6
        accountManager.setUserData(testAccount, AccountSettings.KEY_SETTINGS_VERSION, fromVersion.toString())

        // will run AccountSettings.update
        accountSettingsFactory.create(testAccount)

        val version = accountManager.getUserData(testAccount, AccountSettings.KEY_SETTINGS_VERSION).toIntOrNull()
        assertEquals(AccountSettings.CURRENT_VERSION, version)
    }

}