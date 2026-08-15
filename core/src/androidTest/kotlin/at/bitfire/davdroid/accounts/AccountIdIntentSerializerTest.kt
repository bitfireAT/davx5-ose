/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.content.Intent
import androidx.core.content.IntentCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountIdIntentSerializerTest {
    @Test
    fun test_addExtra_LegacyAccount() {
        val account = LegacyAccount(Account("test", "test"))
        val intent = Intent().apply {
            AccountIdIntentSerializer.addExtra(this, "account", account)
        }
        val restoredAccount = IntentCompat.getParcelableExtra(intent, "account", Account::class.java)
        assertEquals(account.androidAccount, restoredAccount)
    }

    @Test
    fun test_addExtra_DbAccount() {
        val account = DbAccountId(123L)
        val intent = Intent().apply {
            AccountIdIntentSerializer.addExtra(this, "account", account)
        }
        val restoredAccount = intent.getLongExtra("account", 0L).takeIf { it > 0L }?.let { id ->
            DbAccountId(id)
        }
        assertEquals(account, restoredAccount)
    }

    @Test
    fun test_fromIntent_LegacyAccount() {
        val account = LegacyAccount(Account("test", "test"))
        val intent = Intent().apply {
            AccountIdIntentSerializer.addExtra(this, "account", account)
        }
        val restoredAccount = AccountIdIntentSerializer.fromIntent(intent, "account")
        assertEquals(account, restoredAccount)
    }

    @Test
    fun test_fromIntent_DbAccount() {
        val account = DbAccountId(123L)
        val intent = Intent().apply {
            AccountIdIntentSerializer.addExtra(this, "account", account)
        }
        val restoredAccount = AccountIdIntentSerializer.fromIntent(intent, "account")
        assertEquals(account, restoredAccount)
    }
}
