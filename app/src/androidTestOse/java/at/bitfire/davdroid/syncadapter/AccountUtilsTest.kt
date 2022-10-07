/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
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
    lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }


    val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
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