/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import android.accounts.Account
import android.accounts.AccountManager
import app.cash.turbine.test
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val ACCOUNT_TYPE = "at.bitfire.davdroid.address_book"

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class AddressBookAccountManagerTest {

    private val context = RuntimeEnvironment.getApplication()
    private val accountManager = AccountManager.get(context)
    private val logger = Logger.getLogger(javaClass.name)
    private val addressBookAccountManager = AddressBookAccountManager(
        accountManager = accountManager,
        context = context,
        ioDispatcher = Dispatchers.IO,
        logger = logger,
    )

    @After
    fun tearDown() {
        removeAllAddressBooks()
    }

    @Test
    fun `create() should create account`() {
        val accountId = DbAccountId(42)
        val accountName = "test"
        val collectionId = 23L

        val result = addressBookAccountManager.create(accountId, accountName, collectionId)

        assertNotNull(result) { account ->
            assertEquals(accountName, account.name)
            assertEquals(ACCOUNT_TYPE, account.type)
            assertEquals(accountId, addressBookAccountManager.getAppAccount(account))
            assertEquals(collectionId, addressBookAccountManager.getCollectionId(account))
        }
    }

    @Test
    fun `delete() should delete account`() {
        val account = createAccount(accountId = DbAccountId(1), name = "test", collectionId = 1)

        addressBookAccountManager.delete(account)

        assertEquals(emptyList(), getAllAddressBookAccounts())
    }

    @Test
    fun `deleteByAccountId() should delete linked accounts and leave others untouched`() {
        val accountId1 = DbAccountId(1)
        val accountId2 = DbAccountId(2)
        createAccount(accountId = accountId1, name = "test1", collectionId = 1)
        createAccount(accountId = accountId1, name = "test2", collectionId = 2)
        val account3 = createAccount(accountId = accountId2, name = "test3", collectionId = 3)

        addressBookAccountManager.deleteByAccountId(accountId1)

        assertEquals(listOf(account3), getAllAddressBookAccounts())
    }

    @Test
    fun `deleteAllLinkingDbAccount() should delete all accounts linked to a DB account`() {
        val account1 = createAccount(
            accountId = LegacyAccount(Account("irrelevant", "irrelevant")),
            name = "test1",
            collectionId = 1
        )
        createAccount(accountId = DbAccountId(2), name = "test2", collectionId = 2)
        createAccount(accountId = DbAccountId(3), name = "test3", collectionId = 3)

        addressBookAccountManager.deleteAllLinkingDbAccount()

        assertEquals(listOf(account1), getAllAddressBookAccounts())
    }

    @Test
    fun `updateAppAccount() should update linked account`() {
        val oldAccountId = DbAccountId(1)
        val newAccountId = DbAccountId(2)
        val account = createAccount(accountId = oldAccountId, name = "test", collectionId = 1)

        addressBookAccountManager.updateAppAccount(oldAccountId, newAccountId)

        assertEquals(listOf(account), addressBookAccountManager.getAllByAccountId(newAccountId))
        assertEquals(1, getAllAddressBookAccounts().size)
    }

    @Test
    fun `updateProperties() should update linked account and collection ID`() {
        val newAccountId = DbAccountId(2)
        val newCollectionId = 2L
        val account = createAccount(accountId = DbAccountId(1), name = "test", collectionId = 1)

        addressBookAccountManager.updateProperties(account, newAccountId, newCollectionId)

        assertEquals(newCollectionId, addressBookAccountManager.getCollectionId(account))
        assertEquals(listOf(account), addressBookAccountManager.getAllByAccountId(newAccountId))
        assertEquals(1, getAllAddressBookAccounts().size)
    }

    @Test
    fun `getAll() should return all accounts`() {
        val accountId1 = DbAccountId(1)
        val accountId2 = DbAccountId(2)
        val account1 = createAccount(accountId = accountId1, name = "test1", collectionId = 1)
        val account2 = createAccount(accountId = accountId1, name = "test2", collectionId = 2)
        val account3 = createAccount(accountId = accountId2, name = "test3", collectionId = 3)

        val accounts = addressBookAccountManager.getAll()

        assertEquals(setOf(account1, account2, account3), accounts.toSet())
    }

    @Test
    fun `getAllByAccountId() should only return accounts linked to specified app account`() {
        val accountId1 = DbAccountId(1)
        val accountId2 = DbAccountId(2)
        val account1 = createAccount(accountId = accountId1, name = "test1", collectionId = 1)
        val account2 = createAccount(accountId = accountId1, name = "test2", collectionId = 2)
        createAccount(accountId = accountId2, name = "test3", collectionId = 3)

        val accounts = addressBookAccountManager.getAllByAccountId(accountId1)

        assertEquals(listOf(account1, account2), accounts)
    }

    @Test
    fun `getAllByAccountIdFlow()`() = runTest {
        val accountId = DbAccountId(1)
        val account1 = createAccount(accountId = accountId, name = "test1", collectionId = 1)

        val flow = addressBookAccountManager.getAllByAccountIdFlow(accountId)

        flow.test {
            assertEquals(listOf(account1), awaitItem())

            // Create another account
            val account2 = createAccount(accountId = accountId, name = "test2", collectionId = 2)

            assertEquals(setOf(account1, account2), awaitItem().toSet())

            // Delete the first account
            addressBookAccountManager.delete(account1)

            assertEquals(listOf(account2), awaitItem())
        }
    }

    private fun createAccount(accountId: AccountId, name: String, collectionId: Long): Account {
        val account = addressBookAccountManager.create(accountId, name, collectionId)
        assertNotNull(account)
        return account
    }

    private fun getAllAddressBookAccounts(): List<Account?> {
        return accountManager.getAccountsByType(ACCOUNT_TYPE).toList()
    }

    private fun removeAllAddressBooks() {
        accountManager.getAccountsByType(ACCOUNT_TYPE).forEach { account ->
            accountManager.removeAccountExplicitly(account)
        }
    }
}
