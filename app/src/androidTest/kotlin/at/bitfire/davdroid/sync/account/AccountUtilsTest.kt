/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.test.R
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
class AccountUtilsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context
    val testContext = InstrumentationRegistry.getInstrumentation().context

    @Inject
    lateinit var settingsManager: SettingsManager

    val account = Account(
        "AccountUtilsTest",
        testContext.getString(R.string.account_type_test)
    )

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testCreateAccount() {
        val userData = Bundle(2)
        userData.putString("int", "1")
        userData.putString("string", "abc/\"-")

        val manager = AccountManager.get(context)
        try {
            assertTrue(AccountUtils.createAccount(context, account, userData))

            // validate user data
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
        } finally {
            assertTrue(manager.removeAccountExplicitly(account))
        }
    }

}