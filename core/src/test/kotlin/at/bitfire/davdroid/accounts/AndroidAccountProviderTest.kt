/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.db.DbAccountDao
import at.bitfire.davdroid.resource.local.AddressBookAccountManager
import at.bitfire.davdroid.sync.SyncFrameworkSettingsUpdater
import at.bitfire.davdroid.sync.account.InvalidAccountException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class AndroidAccountProviderTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val accountManager = AccountManager.get(context)
    private val dbAccountType = context.getString(R.string.account_type_db)

    private val dbAccountDao = mockk<DbAccountDao>().also { dao ->
        coEvery { dao.get(any()) } returns null
    }
    private val database = mockk<AppDatabase> {
        every { dbAccountDao() } returns dbAccountDao
    }
    private val logger = mockk<Logger>(relaxed = true)
    private val syncFrameworkSettingsUpdater = mockk<SyncFrameworkSettingsUpdater>(relaxed = true)

    private val addressBookAccountManager = AddressBookAccountManager(accountManager, context, Dispatchers.IO, logger)
    private val androidAccountManager = AndroidAccountManager(
        accountManager = { accountManager },
        addressBookAccountManager = addressBookAccountManager,
        context = context,
        database = database,
        ioDispatcher = Dispatchers.IO,
        logger = logger
    )
    private val provider = AndroidAccountProvider(
        androidAccountManager = androidAccountManager,
        db = database,
        ioDispatcher = Dispatchers.IO,
        logger = logger,
        syncFrameworkSettingsUpdater = { syncFrameworkSettingsUpdater }
    )

    private fun addDbAccount(id: Long, name: String) {
        coEvery { dbAccountDao.get(id) } returns DbAccount(id, name)
    }

    private fun addAndroidAccount(name: String, linkedId: Long? = null): Account {
        val account = Account(name, dbAccountType)
        assertTrue(accountManager.addAccountExplicitly(account, null, null))
        if (linkedId != null)
            accountManager.setUserData(account, AndroidAccountManager.USER_DATA_ACCOUNT_ID, linkedId.toString())
        return account
    }

    @After
    fun tearDown() {
        for (type in arrayOf(dbAccountType, context.getString(R.string.account_type_address_book)))
            for (account in accountManager.getAccountsByType(type))
                accountManager.removeAccountExplicitly(account)
    }


    @Test
    fun `provide() returns Android account of legacy account`() = runTest {
        val legacy = Account("Legacy", context.getString(R.string.account_type))

        val result = provider.provide(LegacyAccount(legacy))

        assertEquals(legacy, result)
    }

    @Test
    fun `provide() returns linked account`() = runTest {
        addDbAccount(1, "DB Name")
        val linked = addAndroidAccount("Android Name", linkedId = 1)

        val result = provider.provide(DbAccountId(1))

        assertEquals(linked, result)
    }

    @Test
    fun `provide() doesn't update sync framework settings when linked account exists`() = runTest {
        addDbAccount(1, "DB Name")
        addAndroidAccount("Android Name", linkedId = 1)

        provider.provide(DbAccountId(1))

        verify(exactly = 0) { syncFrameworkSettingsUpdater.update(any()) }
    }

    @Test
    fun `provide() creates linked account when missing`() = runTest {
        addDbAccount(1, "DB Name")

        val result = provider.provide(DbAccountId(1))

        assertEquals(Account("DB Name", dbAccountType), result)
        assertEquals(1L, androidAccountManager.getLinkedDbAccountId(result))
    }

    @Test
    fun `provide() updates sync framework settings when account is created`() = runTest {
        addDbAccount(1, "DB Name")

        val result = provider.provide(DbAccountId(1))

        verify { syncFrameworkSettingsUpdater.update(DbAccountId(1)) }
    }

    @Test
    fun `provide() replaces unlinked account with same name`() = runTest {
        addDbAccount(1, "DB Name")
        addAndroidAccount("DB Name")

        val result = provider.provide(DbAccountId(1))

        assertEquals(Account("DB Name", dbAccountType), result)
        assertEquals(1L, androidAccountManager.getLinkedDbAccountId(result))
    }

    @Test
    fun `provide() replaces account with same name that is linked to deleted DB account`() = runTest {
        addDbAccount(1, "DB Name")
        addAndroidAccount("DB Name", linkedId = 99)

        val result = provider.provide(DbAccountId(1))

        assertEquals(Account("DB Name", dbAccountType), result)
        assertEquals(1L, androidAccountManager.getLinkedDbAccountId(result))
    }

    @Test
    fun `provide() removes address book accounts of replaced account`() = runTest {
        addDbAccount(1, "DB Name")
        addAndroidAccount("DB Name", linkedId = 99)
        val addressBook = Account("Address book", context.getString(R.string.account_type_address_book))
        assertTrue(accountManager.addAccountExplicitly(addressBook, null, null))
        addressBookAccountManager.setAppAccount(addressBook, DbAccountId(99))

        provider.provide(DbAccountId(1))

        assertFalse(accountManager.accounts.contains(addressBook))
    }

    @Test
    fun `provide() throws InvalidAccountException when account with same name is linked to other DB account`() = runTest {
        addDbAccount(1, "DB Name")
        addDbAccount(2, "Other DB Name")
        addAndroidAccount("DB Name", linkedId = 2)

        assertFailsWith<InvalidAccountException> {
            provider.provide(DbAccountId(1))
        }
    }

    @Test
    fun `provide() throws InvalidAccountException when DB account does not exist`() = runTest {
        assertFailsWith<InvalidAccountException> {
            provider.provide(DbAccountId(1))
        }
    }

}
