/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.Account
import at.bitfire.davdroid.accounts.AndroidAccountInfo
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.ui.accountscheck.AccountsCheckViewModel.Companion.toAccountStatuses
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountsCheckViewModelTest {

    @Test
    fun `toAccountStatuses() returns legacy account`() {
        val legacy = legacyAccount("Legacy")

        val result = toAccountStatuses(listOf(legacy), emptyList())

        assertEquals(listOf(AccountStatus.Legacy("Legacy")), result)
    }

    @Test
    fun `toAccountStatuses() returns account that links to DB account`() {
        val linked = dbTypeAccount("Linked", linkedId = 1)
        val dbAccounts = listOf(DbAccount(id = 1, name = "DB account name"))

        val result = toAccountStatuses(listOf(linked), dbAccounts)

        assertEquals(listOf(AccountStatus.Linked("Linked")), result)
    }

    @Test
    fun `toAccountStatuses() returns unlinked account as linked when DB account has the same name`() {
        val unlinked = dbTypeAccount("Unlinked")
        val dbAccounts = listOf(DbAccount(id = 1, name = "Unlinked"))

        val result = toAccountStatuses(listOf(unlinked), dbAccounts)

        assertEquals(listOf(AccountStatus.Linked("Unlinked")), result)
    }

    @Test
    fun `toAccountStatuses() sorts accounts by name`() {
        val androidAccounts = listOf(legacyAccount("c"), dbTypeAccount("B", linkedId = 1), legacyAccount("a"))
        val dbAccounts = listOf(DbAccount(id = 1, name = "B"))

        val result = toAccountStatuses(androidAccounts, dbAccounts)

        assertEquals(listOf("a", "B", "c"), result.map { it.name })
    }


    private fun legacyAccount(name: String) =
        AndroidAccountInfo(Account(name, "legacy"), isLegacy = true)

    private fun dbTypeAccount(name: String, linkedId: Long? = null) =
        AndroidAccountInfo(Account(name, "db"), isLegacy = false, linkedDbAccountId = linkedId)

}
