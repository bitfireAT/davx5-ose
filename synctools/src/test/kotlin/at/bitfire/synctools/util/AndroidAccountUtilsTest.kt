/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidAccountUtilsTest {

    val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun testCreateAccount() {
        val userData = mapOf(
            "int" to "1",
            "string" to "abc/\"-"
        )

        val account = Account("testCreateAccount", javaClass.name)
        val manager = AccountManager.get(context)
        try {
            assertTrue(AndroidAccountUtils.createAccount(context, account, userData, "secret".toSensitiveString()))

            // validate user data
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
            assertEquals("secret", manager.getPassword(account))
        } finally {
            assertTrue(manager.removeAccountExplicitly(account))
        }
    }

}