/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration17Test {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var migration: AccountSettingsMigration17

    lateinit var account: Account
    val accountManager by lazy { AccountManager.get(context) }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(android.Manifest.permission.WRITE_CONTACTS)


    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()
        val fromVersion = 16
        accountManager.setUserData(account, AccountSettings.KEY_SETTINGS_VERSION, fromVersion.toString())
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testMigrate_OldAddressBook_CollectionInDB() {
        val addressBookAccount = Account("Address Book", context.getString(R.string.account_type_address_book))
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))
        try {
            // address book has account + URL
            accountManager.setUserData(addressBookAccount, "real_account_name", account.name)
            accountManager.setUserData(addressBookAccount, LocalAddressBook.USER_DATA_URL, "https://example.com/address-book")

            // and is known in database
            db.serviceDao().insertOrReplace(Service(
                id = 1, accountName = account.name, type = Service.TYPE_CARDDAV, principal = null
            ))
            db.collectionDao().insert(Collection(
                id = 100, serviceId = 1, url = "https://example.com/address-book".toHttpUrl(), type = Collection.TYPE_ADDRESSBOOK
            ))

            // run migration
            migration.migrate(account, mockk())

            // ID is now assigned
            assertEquals(100, accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID)?.toLong())
        } finally {
            accountManager.removeAccountExplicitly(addressBookAccount)
        }
    }

}