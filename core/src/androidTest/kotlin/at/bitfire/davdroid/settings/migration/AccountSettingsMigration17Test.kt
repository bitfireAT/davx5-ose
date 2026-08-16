/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.davdroid.util.DavUtils.toUrl
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration17Test {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var migration: AccountSettingsMigration17

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS)


    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testMigrate_OldAddressBook_CollectionInDB() {
        val localAddressBookUserDataUrl = "url"
        TestAccount.provide(version = 16) { account ->
            val addressBookAccountType = context.getString(R.string.account_type_address_book)
            var addressBookAccount = Account("Address Book", addressBookAccountType)
            assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))

            try {
                // address book has account + URL
                val url = "https://example.com/address-book"
                accountManager.setAndVerifyUserData(addressBookAccount, "real_account_name", account.name)
                accountManager.setAndVerifyUserData(addressBookAccount, localAddressBookUserDataUrl, url)

                // and is known in database
                db.serviceDao().insertOrReplaceBlocking(
                    Service(
                        id = 1, accountName = account.name, type = Service.TYPE_CARDDAV, principal = null
                    )
                )
                db.collectionDao().insertBlocking(
                    Collection(
                        id = 100,
                        serviceId = 1,
                        url = url.toUrl(),
                        type = Collection.TYPE_ADDRESSBOOK,
                        displayName = "Some Address Book"
                    )
                )
                val store = mockk<AccountSettingsStore>()

                // run migration
                migration.migrate(LegacyAccount(account), store)

                // migration renames address book, update account
                addressBookAccount = accountManager.getAccountsByType(addressBookAccountType).filter {
                    accountManager.getUserData(it, localAddressBookUserDataUrl) == url
                }.first()
                assertEquals("Some Address Book (${account.name}) #100", addressBookAccount.name)

                // ID is now assigned
                assertEquals(100L, accountManager.getUserData(addressBookAccount, "collection_id")?.toLong())
            } finally {
                accountManager.removeAccountExplicitly(addressBookAccount)
            }
        }
    }

}