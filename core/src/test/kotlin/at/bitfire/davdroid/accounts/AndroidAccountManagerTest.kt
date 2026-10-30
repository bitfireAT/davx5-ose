/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.db.DbAccountDao
import at.bitfire.davdroid.resource.local.AddressBookAccountProperties
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Level
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class AndroidAccountManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val logger = mockk<Logger>(relaxed = true)

    private val account = Account("Test Account", javaClass.name)
    private val denied = SecurityException("Account creation denied by Android")

    private val dbAccountType = context.getString(R.string.account_type_db)
    private val dbAccountDao = mockk<DbAccountDao>()
    private val database = mockk<AppDatabase> {
        every { dbAccountDao() } returns dbAccountDao
    }
    private val realManager = AccountManager.get(context)

    private fun androidAccountManager(manager: AccountManager = realManager) = AndroidAccountManager(
        accountManager = { manager },
        addressBookAccountProperties = AddressBookAccountProperties(realManager, context),
        context = context,
        database = database,
        ioDispatcher = Dispatchers.IO,
        logger = logger
    )

    private fun addAndroidAccount(name: String, linkedId: Long? = null): Account {
        val account = Account(name, dbAccountType)
        assertTrue(realManager.addAccountExplicitly(account, null, null))
        if (linkedId != null)
            realManager.setUserData(account, AndroidAccountManager.USER_DATA_ACCOUNT_ID, linkedId.toString())
        return account
    }

    private fun addAddressBookAccount(name: String, owner: AccountId): Account {
        val account = Account(name, context.getString(R.string.account_type_address_book))
        assertTrue(realManager.addAccountExplicitly(account, null, null))
        AddressBookAccountProperties(realManager, context).setAppAccount(account, owner)
        return account
    }

    @After
    fun tearDown() {
        for (type in arrayOf(R.string.account_type, R.string.account_type_address_book, R.string.account_type_db))
            for (account in realManager.getAccountsByType(context.getString(type)))
                realManager.removeAccountExplicitly(account)
    }


    @Test
    fun `createAccount() creates account with user data and password`() {
        val manager = AccountManager.get(context)
        val userData = mapOf(
            "int" to "1",
            "string" to "abc/\"-"
        )

        try {
            val created = androidAccountManager(manager).createAccount(account, userData, "secret".toSensitiveString())

            assertTrue(created)
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
            assertEquals("secret", manager.getPassword(account))
        } finally {
            assertTrue(manager.removeAccountExplicitly(account))
        }
    }

    @Test
    fun `createAccount() logs matching account providers when denied`() {
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } throws denied
            every { authenticatorTypes } returns arrayOf(
                AuthenticatorDescription(account.type, "matching.provider", 0, 0, 0, 0),
                AuthenticatorDescription("other.type", "unrelated.provider", 0, 0, 0, 0)
            )
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify {
            logger.log(Level.WARNING, any<String>(), denied)
            logger.warning(match<String> { "matching.provider" in it && "unrelated.provider" !in it })
        }
    }

    @Test
    fun `createAccount() logs unknown account provider when denied and none visible`() {
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } throws denied
            every { authenticatorTypes } returns emptyArray()
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify {
            logger.warning(match<String> { "unknown" in it })
        }
    }

    @Test
    fun `createAccount() logs failed account provider lookup when denied`() {
        val lookupFailed = IllegalStateException("Lookup failed")
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } throws denied
            every { authenticatorTypes } throws lookupFailed
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify {
            logger.log(Level.WARNING, any<String>(), lookupFailed)
        }
    }

    @Test
    fun `createAccount() returns false without logging when account is not created`() {
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } returns false
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify(exactly = 0) {
            manager.authenticatorTypes
            logger.log(any(), any<String>(), any<Throwable>())
            logger.warning(any<String>())
        }
    }


    @Test
    fun `createLinkedAccount() creates Android account linked to DB account`() {
        val created = androidAccountManager().createLinkedAccount("Linked", 42)

        assertEquals(Account("Linked", dbAccountType), created)
        assertEquals("42", realManager.getUserData(created, AndroidAccountManager.USER_DATA_ACCOUNT_ID))
    }

    @Test
    fun `createLinkedAccount() returns null when account already exists`() {
        addAndroidAccount("Existing")

        val created = androidAccountManager().createLinkedAccount("Existing", 42)

        assertNull(created)
    }


    @Test
    fun `findAccountByName() returns Android account with name`() {
        val existing = addAndroidAccount("Existing", linkedId = 1)

        val found = androidAccountManager().findAccountByName("Existing")

        assertEquals(existing, found)
    }

    @Test
    fun `findAccountByName() returns null when there is no account with name`() {
        addAndroidAccount("Other")

        val found = androidAccountManager().findAccountByName("Existing")

        assertNull(found)
    }


    @Test
    fun `findLinkedAccount() returns account linked to DB account`() {
        addAndroidAccount("Other", linkedId = 1)
        val linked = addAndroidAccount("Linked", linkedId = 2)

        val found = androidAccountManager().findLinkedAccount(2)

        assertEquals(linked, found)
    }

    @Test
    fun `findLinkedAccount() returns null when no account is linked to DB account`() {
        addAndroidAccount("Unlinked")
        addAndroidAccount("Other", linkedId = 1)

        val found = androidAccountManager().findLinkedAccount(2)

        assertNull(found)
    }


    @Test
    fun `getLinkedDbAccountId() returns linked DB account ID`() {
        val linked = addAndroidAccount("Linked", linkedId = 5)

        val id = androidAccountManager().getLinkedDbAccountId(linked)

        assertEquals(5L, id)
    }

    @Test
    fun `getLinkedDbAccountId() returns null for unlinked account`() {
        val unlinked = addAndroidAccount("Unlinked")

        val id = androidAccountManager().getLinkedDbAccountId(unlinked)

        assertNull(id)
    }


    @Test
    fun `linkAccount() links account to DB account`() {
        val unlinked = addAndroidAccount("Unlinked")

        androidAccountManager().linkAccount(unlinked, 7)

        assertEquals("7", realManager.getUserData(unlinked, AndroidAccountManager.USER_DATA_ACCOUNT_ID))
    }


    @Test
    fun `removeAllForDbAccounts() removes Android accounts and address book accounts of DB accounts`() {
        val linked = addAndroidAccount("Linked", linkedId = 1)
        val unlinked = addAndroidAccount("Unlinked")
        val addressBook = addAddressBookAccount("Address book", DbAccountId(1))

        androidAccountManager().removeAllForDbAccounts()

        val remaining = realManager.accounts.toList()
        assertFalse(remaining.contains(linked))
        assertFalse(remaining.contains(unlinked))
        assertFalse(remaining.contains(addressBook))
    }

    @Test
    fun `removeAllForDbAccounts() keeps legacy accounts and their address book accounts`() {
        val legacy = Account("Legacy", context.getString(R.string.account_type))
        assertTrue(realManager.addAccountExplicitly(legacy, null, null))
        val addressBook = addAddressBookAccount("Address book", LegacyAccount(legacy))

        androidAccountManager().removeAllForDbAccounts()

        val remaining = realManager.accounts.toList()
        assertTrue(remaining.contains(legacy))
        assertTrue(remaining.contains(addressBook))
    }


    @Test
    fun `getAndroidAccount() returns Android account of legacy account`() {
        val legacy = Account("Legacy", context.getString(R.string.account_type))

        val result = androidAccountManager().getAndroidAccount(LegacyAccount(legacy))

        assertEquals(legacy, result)
    }

    @Test
    fun `getAndroidAccount() returns linked account of DB account`() {
        every { dbAccountDao.getBlocking(3) } returns DbAccount(3, "DB Name")
        val linked = addAndroidAccount("Android Name", linkedId = 3)

        val result = androidAccountManager().getAndroidAccount(DbAccountId(3))

        assertEquals(linked, result)
    }

    @Test
    fun `getAndroidAccount() returns account with DB account name when there is no linked account`() {
        every { dbAccountDao.getBlocking(3) } returns DbAccount(3, "DB Name")

        val result = androidAccountManager().getAndroidAccount(DbAccountId(3))

        assertEquals(Account("DB Name", dbAccountType), result)
    }

    @Test
    fun `getAndroidAccount() throws InvalidAccountException when DB account does not exist`() {
        every { dbAccountDao.getBlocking(3) } returns null

        assertThrows(InvalidAccountException::class.java) {
            androidAccountManager().getAndroidAccount(DbAccountId(3))
        }
    }


    @Test
    fun `getAccountId() returns LegacyAccount for legacy account`() = runTest {
        val legacy = Account("Legacy", context.getString(R.string.account_type))

        val result = androidAccountManager().getAccountId(legacy)

        assertEquals(LegacyAccount(legacy), result)
    }

    @Test
    fun `getAccountId() returns linked DB account`() = runTest {
        coEvery { dbAccountDao.get(3) } returns DbAccount(3, "DB Name")
        val linked = addAndroidAccount("Android Name", linkedId = 3)

        val result = androidAccountManager().getAccountId(linked)

        assertEquals(DbAccountId(3), result)
    }

    @Test
    fun `getAccountId() looks up DB account by name when linked DB account does not exist`() = runTest {
        coEvery { dbAccountDao.get(3) } returns null
        coEvery { dbAccountDao.getFromName("Name") } returns DbAccount(4, "Name")
        val linked = addAndroidAccount("Name", linkedId = 3)

        val result = androidAccountManager().getAccountId(linked)

        assertEquals(DbAccountId(4), result)
    }

    @Test
    fun `getAccountId() returns null when DB account does not exist`() = runTest {
        coEvery { dbAccountDao.get(3) } returns null
        coEvery { dbAccountDao.getFromName("Name") } returns null
        val linked = addAndroidAccount("Name", linkedId = 3)

        val result = androidAccountManager().getAccountId(linked)

        assertNull(result)
    }

    @Test
    fun `getAccountId() looks up DB account by name when account is not linked`() = runTest {
        coEvery { dbAccountDao.getFromName("Name") } returns DbAccount(4, "Name")
        val unlinked = addAndroidAccount("Name")

        val result = androidAccountManager().getAccountId(unlinked)

        assertEquals(DbAccountId(4), result)
    }

}
