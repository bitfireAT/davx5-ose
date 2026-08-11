/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import androidx.core.database.getLongOrNull
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.accounts.toAccountId
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.davdroid.settings.migration.AccountSettingsMigration18.Companion.USER_DATA_ACCOUNT_NAME
import at.bitfire.davdroid.settings.migration.AccountSettingsMigration18.Companion.USER_DATA_ACCOUNT_TYPE
import at.bitfire.davdroid.settings.migration.AccountSettingsMigration18.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.settings.migration.AccountSettingsMigration20.Companion.ADDRESS_BOOK_USER_DATA_URL
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.davdroid.util.DavUtils.toUrl
import at.bitfire.synctools.storage.calendar.EventsContract.asSyncAdapter
import at.bitfire.synctools.util.setAndVerifyUserData
import at.bitfire.synctools.vcard.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration20Test {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var calendarStore: LocalCalendarStore

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var migration: AccountSettingsMigration20

    @Inject
    lateinit var localTestAddressBook: LocalTestAddressBook

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val permissionsRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR
    )

    private lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create(version = 19)
    }

    @After
    fun cleanUp() {
        TestAccount.remove(account)
    }


    @Test
    fun testMigrateAddressBooks_UrlMatchesCollection() {
        // set up legacy address-book with URL, but without collection ID
        val url = "https://example.com/"

        db.serviceDao()
            .insertOrReplaceBlocking(
                Service(
                    id = 1,
                    accountName = account.name,
                    type = Service.TYPE_CARDDAV,
                    principal = null
                )
            )
        val collectionId = db.collectionDao().insertBlocking(
            Collection(
            serviceId = 1,
            type = Collection.Companion.TYPE_ADDRESSBOOK,
            url = url.toUrl()
        ))

        localTestAddressBook.provide(account.toAccountId(), mockk(relaxed = true), GroupMethod.GROUP_VCARDS) { addressBook ->

            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, USER_DATA_ACCOUNT_NAME, account.name)
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, USER_DATA_ACCOUNT_TYPE, account.type)
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, ADDRESS_BOOK_USER_DATA_URL, url)
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, USER_DATA_COLLECTION_ID, null)

            migration.migrateAddressBooks(account, cardDavServiceId = 1)

            assertEquals(
                collectionId,
                accountManager.getUserData(addressBook.addressBookAccount, USER_DATA_COLLECTION_ID).toLongOrNull()
            )
        }
    }


    @Test
    fun testMigrateCalendars_UrlMatchesCollection() {
        // set up legacy calendar with URL, but without collection ID
        val url = "https://example.com/"

        db.serviceDao()
            .insertOrReplaceBlocking(
                Service(
                    id = 1,
                    accountName = account.name,
                    type = Service.TYPE_CALDAV,
                    principal = null
                )
            )
        val collectionId = db.collectionDao().insertBlocking(
            Collection(
                serviceId = 1,
                type = Collection.Companion.TYPE_CALENDAR,
                url = url.toUrl()
            )
        )

        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!.use { provider ->
            val uri = provider.insert(
                Calendars.CONTENT_URI.asSyncAdapter(account),
                contentValuesOf(
                    Calendars.ACCOUNT_NAME to account.name,
                    Calendars.ACCOUNT_TYPE to account.type,
                    Calendars.CALENDAR_DISPLAY_NAME to "Test",
                    Calendars.NAME to url,
                    Calendars.SYNC_EVENTS to 1
                )
            )!!.asSyncAdapter(account)
            try {
                migration.migrateCalendars(account, 1)

                provider.query(uri, arrayOf(Calendars._SYNC_ID), null, null, null)!!.use { cursor ->
                    cursor.moveToNext()
                    assertEquals(collectionId, cursor.getLongOrNull(0))
                }
            } finally {
                provider.delete(uri, null, null)
            }
        }
    }

}