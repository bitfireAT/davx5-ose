/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
class AccountUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }


    val account by lazy { Account("Test Account", context.getString(R.string.account_type)) }


    @Test
    fun testCreateAccount() {
        val userData = Bundle(2)
        userData.putString("int", "1")
        userData.putString("string", "abc/\"-")
        try {
            assertTrue(AccountUtils.createAccount(context, account, userData))

            // validate user data
            val manager = AccountManager.get(context)
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
        } finally {
            val futureResult = AccountManager.get(context).removeAccount(account, {}, null)
            assertTrue(futureResult.getResult(10, TimeUnit.SECONDS))
        }
    }

}