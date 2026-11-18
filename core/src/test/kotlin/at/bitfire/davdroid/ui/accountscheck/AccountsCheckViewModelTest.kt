/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.Account
import at.bitfire.davdroid.accounts.AndroidAccountInfo
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.repository.DbAccountRepository
import at.bitfire.davdroid.ui.accountscheck.AccountsCheckViewModel.Companion.toAccountStatuses
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountsCheckViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val androidAccountManager = mockk<AndroidAccountManager>(relaxed = true) {
        every { getAllFlow() } returns emptyFlow()
    }
    private val dbAccountRepository = mockk<DbAccountRepository> {
        every { getAllDbAccountsFlow() } returns emptyFlow()
    }

    private val orphan = Account("Orphan", "db")

    private lateinit var model: AccountsCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        model = AccountsCheckViewModel(
            androidAccountManager = androidAccountManager,
            dbAccountRepository = dbAccountRepository,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }


    @Test
    fun `deleteAndroidAccount() removes Android account`() {
        model.deleteAndroidAccount(orphan)

        verify { androidAccountManager.removeAccount(orphan) }
    }


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
    fun `toAccountStatuses() returns account that links to non-existing DB account as missing DB account`() {
        val linked = dbTypeAccount("Linked", linkedId = 2)
        val dbAccounts = listOf(DbAccount(id = 1, name = "Linked"))

        val result = toAccountStatuses(listOf(linked), dbAccounts)

        assertEquals(listOf(AccountStatus.MissingDbAccount(linked.account)), result)
    }

    @Test
    fun `toAccountStatuses() returns unlinked account as missing DB account, also when DB account has the same name`() {
        val unlinked = dbTypeAccount("Unlinked")
        val dbAccounts = listOf(DbAccount(id = 1, name = "Unlinked"))

        val result = toAccountStatuses(listOf(unlinked), dbAccounts)

        assertEquals(listOf(AccountStatus.MissingDbAccount(unlinked.account)), result)
    }

    @Test
    fun `toAccountStatuses() sorts problems first, then by name`() {
        val androidAccounts = listOf(
            legacyAccount("c"),
            dbTypeAccount("B", linkedId = 1),
            dbTypeAccount("e", linkedId = 2),
            legacyAccount("a")
        )
        val dbAccounts = listOf(DbAccount(id = 1, name = "B"))

        val result = toAccountStatuses(androidAccounts, dbAccounts)

        assertEquals(listOf("e", "a", "B", "c"), result.map { it.name })
    }


    private fun legacyAccount(name: String) =
        AndroidAccountInfo(Account(name, "legacy"), isLegacy = true)

    private fun dbTypeAccount(name: String, linkedId: Long? = null) =
        AndroidAccountInfo(Account(name, "db"), isLegacy = false, linkedDbAccountId = linkedId)

}
