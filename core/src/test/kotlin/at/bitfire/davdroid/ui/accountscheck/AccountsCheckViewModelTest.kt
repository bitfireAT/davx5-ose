/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.Account
import app.cash.turbine.test
import at.bitfire.davdroid.accounts.AndroidAccountInfo
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.repository.DbAccountRepository
import at.bitfire.davdroid.ui.NotificationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

    private val androidAccounts = MutableStateFlow<List<AndroidAccountInfo>>(emptyList())
    private val androidAccountManager = mockk<AndroidAccountManager>(relaxed = true) {
        every { getAllFlow() } returns androidAccounts
    }
    private val dbAccounts = MutableStateFlow<List<DbAccount>>(emptyList())
    private val dbAccountRepository = mockk<DbAccountRepository> {
        every { getAllDbAccountsFlow() } returns dbAccounts
    }
    private val notificationRegistry = mockk<NotificationRegistry>(relaxed = true)

    private val orphan = Account("Orphan", "db")

    private lateinit var model: AccountsCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        model = AccountsCheckViewModel(
            accountStatuses = AccountStatusesUseCase(androidAccountManager, dbAccountRepository),
            androidAccountManager = androidAccountManager,
            ioDispatcher = testDispatcher,
            notificationRegistry = notificationRegistry
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }


    @Test
    fun `accounts returns legacy account`() = runTest(testDispatcher) {
        androidAccounts.value = listOf(legacyAccount("Legacy"))

        model.accounts.filterNotNull().test {
            assertEquals(listOf(AccountStatus.Legacy("Legacy")), awaitItem())
        }
    }

    @Test
    fun `accounts returns account that links to existing DB account`() = runTest(testDispatcher) {
        coEvery { androidAccountManager.isOrphaned(1) } returns false
        androidAccounts.value = listOf(dbTypeAccount("Linked", linkedId = 1))

        model.accounts.filterNotNull().test {
            assertEquals(listOf(AccountStatus.Linked("Linked")), awaitItem())
        }
    }

    @Test
    fun `accounts returns orphaned account as missing DB account`() = runTest(testDispatcher) {
        val unlinked = dbTypeAccount("Unlinked")
        coEvery { androidAccountManager.isOrphaned(null) } returns true
        androidAccounts.value = listOf(unlinked)

        model.accounts.filterNotNull().test {
            assertEquals(listOf(AccountStatus.MissingDbAccount(unlinked.account)), awaitItem())
        }
    }

    @Test
    fun `accounts sorts problems first, then by name`() = runTest(testDispatcher) {
        coEvery { androidAccountManager.isOrphaned(1) } returns false
        coEvery { androidAccountManager.isOrphaned(2) } returns true
        androidAccounts.value = listOf(
            legacyAccount("c"),
            dbTypeAccount("B", linkedId = 1),
            dbTypeAccount("e", linkedId = 2),
            legacyAccount("a")
        )

        model.accounts.filterNotNull().test {
            assertEquals(listOf("e", "a", "B", "c"), awaitItem().map { it.name })
        }
    }

    @Test
    fun `accounts updates when Android accounts change`() = runTest(testDispatcher) {
        model.accounts.filterNotNull().test {
            assertEquals(emptyList<AccountStatus>(), awaitItem())

            androidAccounts.value = listOf(legacyAccount("New"))

            assertEquals(listOf(AccountStatus.Legacy("New")), awaitItem())
        }
    }

    @Test
    fun `accounts updates when DB accounts change`() = runTest(testDispatcher) {
        val linked = dbTypeAccount("Linked", linkedId = 1)
        coEvery { androidAccountManager.isOrphaned(1) } returns true
        androidAccounts.value = listOf(linked)

        model.accounts.filterNotNull().test {
            assertEquals(listOf(AccountStatus.MissingDbAccount(linked.account)), awaitItem())

            coEvery { androidAccountManager.isOrphaned(1) } returns false
            dbAccounts.value = listOf(DbAccount(id = 1, name = "Linked"))

            assertEquals(listOf(AccountStatus.Linked("Linked")), awaitItem())
        }
    }

    @Test
    fun `accounts cancels account problems notification when there are no problems`() = runTest(testDispatcher) {
        androidAccounts.value = listOf(legacyAccount("Legacy"))

        model.accounts.filterNotNull().test {
            awaitItem()
        }

        verify { notificationRegistry.cancelAccountsCheck() }
    }

    @Test
    fun `accounts doesn't cancel account problems notification while there are problems`() = runTest(testDispatcher) {
        coEvery { androidAccountManager.isOrphaned(null) } returns true
        androidAccounts.value = listOf(dbTypeAccount("Unlinked"))

        model.accounts.filterNotNull().test {
            awaitItem()
        }

        verify(exactly = 0) { notificationRegistry.cancelAccountsCheck() }
    }

    @Test
    fun `deleteAndroidAccount() removes Android account`() {
        model.deleteAndroidAccount(orphan)

        verify { androidAccountManager.removeAccount(orphan) }
    }


    private fun legacyAccount(name: String) =
        AndroidAccountInfo(Account(name, "legacy"), isLegacy = true)

    private fun dbTypeAccount(name: String, linkedId: Long? = null) =
        AndroidAccountInfo(Account(name, "db"), isLegacy = false, linkedDbAccountId = linkedId)

}
