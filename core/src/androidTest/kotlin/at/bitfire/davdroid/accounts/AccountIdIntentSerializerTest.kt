/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountIdIntentSerializerTest {
    @Test
    fun test_roundTrip_LegacyAccount() {
        val account = LegacyAccount(Account("test", "test"))
        val intent = Intent().apply {
            AccountIdIntentSerializer.addExtra(this, "account", account)
        }
        val restoredAccount = AccountIdIntentSerializer.fromIntent(intent, "account")
        assertEquals(account, restoredAccount)
    }

    @Test
    fun test_roundTrip_DbAccount() {
        val account = DbAccountId(123L)
        val intent = Intent().apply {
            AccountIdIntentSerializer.addExtra(this, "account", account)
        }
        val restoredAccount = AccountIdIntentSerializer.fromIntent(intent, "account")
        assertEquals(account, restoredAccount)
    }
}
