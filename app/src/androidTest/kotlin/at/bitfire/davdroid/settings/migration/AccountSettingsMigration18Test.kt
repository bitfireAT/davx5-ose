/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBook
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration18Test {

    @Inject @ApplicationContext
    lateinit var context: Context

    @MockK
    lateinit var db: AppDatabase

    @InjectMockKs
    lateinit var migration: AccountSettingsMigration18

    @get:Rule
    val hiltRule = HiltAndroidRule(this)


    @Before
    fun setUp() {
        hiltRule.inject()
        MockKAnnotations.init(this)
    }


    @Test
    fun testMigrate_AddressBook_InvalidCollection() {
        every { db.serviceDao() } returns mockk {
            every { getByAccountAndType(any(), any()) } returns null
        }

        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        var addressBookAccount = Account("Address Book", addressBookAccountType)

        val accountManager = AccountManager.get(context)
        mockkObject(accountManager)
        every { accountManager.getAccountsByType(addressBookAccountType) } returns arrayOf(addressBookAccount)
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID) } returns "123"

        val account = Account("test", "test")
        migration.migrate(account, mockk())

        verify(exactly = 0) {
            accountManager.setUserData(addressBookAccount, any(), any())
        }
    }

    @Test
    fun testMigrate_AddressBook_NoCollection() {
        every { db.serviceDao() } returns mockk {
            every { getByAccountAndType(any(), any()) } returns null
        }

        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        var addressBookAccount = Account("Address Book", addressBookAccountType)

        val accountManager = AccountManager.get(context)
        mockkObject(accountManager)
        every { accountManager.getAccountsByType(addressBookAccountType) } returns arrayOf(addressBookAccount)
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID) } returns "123"

        val account = Account("test", "test")
        migration.migrate(account, mockk())

        verify(exactly = 0) {
            accountManager.setUserData(addressBookAccount, any(), any())
        }
    }

    @Test
    fun testMigrate_AddressBook_ValidCollection() {
        val account = Account("test", "test")

        every { db.serviceDao() } returns mockk {
            every { getByAccountAndType(any(), any()) } returns Service(
                id = 10,
                accountName = account.name,
                type = Service.TYPE_CARDDAV,
                principal = null
            )
        }
        every { db.collectionDao() } returns mockk {
            every { getByService(10) } returns listOf(Collection(
                id = 100,
                serviceId = 10,
                url = "http://example.com".toHttpUrl(),
                type = Collection.TYPE_ADDRESSBOOK
            ))
        }

        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        var addressBookAccount = Account("Address Book", addressBookAccountType)

        val accountManager = AccountManager.get(context)
        mockkObject(accountManager)
        every { accountManager.getAccountsByType(addressBookAccountType) } returns arrayOf(addressBookAccount)
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID) } returns "100"

        migration.migrate(account, mockk())

        verify {
            accountManager.setUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME, account.name)
            accountManager.setUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE, account.type)
        }
    }

}