/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SystemAccountUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        ContentResolver.setMasterSyncAutomatically(false)
        hiltRule.inject()
    }


    @Test
    fun testCreateAccount() {
        val userData = Bundle(2)
        userData.putString("int", "1")
        userData.putString("string", "abc/\"-")

        val account = Account("AccountUtilsTest", context.getString(R.string.account_type))
        val manager = AccountManager.get(context)
        try {
            assertTrue(SystemAccountUtils.createAccount(context, account, userData))

            // validate user data
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
        } finally {
            assertTrue(manager.removeAccountExplicitly(account))
        }
    }

}