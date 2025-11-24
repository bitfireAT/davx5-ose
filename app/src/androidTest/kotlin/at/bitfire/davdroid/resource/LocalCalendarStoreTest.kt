/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.test.InitCalendarProviderRule
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import javax.inject.Inject

@HiltAndroidTest
class LocalCalendarStoreTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val initCalendarProviderRule: TestRule = InitCalendarProviderRule.initialize()

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var localCalendarStore: LocalCalendarStore

    private lateinit var provider: ContentProviderClient
    private lateinit var account: Account
    private lateinit var calendarUri: Uri

    @Before
    fun setUp() {
        hiltRule.inject()
        provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        account = TestAccount.create(accountName = "InitialAccountName")
        calendarUri = createCalendarForAccount(account)
    }

    @After
    fun tearDown() {
        provider.delete(calendarUri, null, null)
        TestAccount.remove(account)
        provider.closeCompat()
    }


    @Ignore("Sometimes failing, see https://github.com/bitfireAT/davx5-ose/issues/1828")
    @Test
    fun testUpdateAccount_updatesOwnerAccount() {
        // Verify initial state
        verifyOwnerAccountIs("InitialAccountName")

        // Rename account
        val oldAccount = account
        account = TestAccount.rename(account, "ChangedAccountName")

        // Update account name in local calendar
        localCalendarStore.updateAccount(oldAccount, account)

        // Verify [Calendar.OWNER_ACCOUNT] of local calendar was updated
        verifyOwnerAccountIs("ChangedAccountName")
    }


    // helpers

    private fun createCalendarForAccount(account: Account): Uri {
        var uri: Uri? = null
        provider.use { providerClient ->
            val values = contentValuesOf(
                Calendars.ACCOUNT_NAME to account.name,
                Calendars.ACCOUNT_TYPE to account.type,
                Calendars.OWNER_ACCOUNT to account.name,
                Calendars.VISIBLE to 1,
                Calendars.SYNC_EVENTS to 1,
                Calendars._SYNC_ID to 999,
                Calendars.CALENDAR_DISPLAY_NAME to "displayName",
            )

            uri = providerClient.insert(
                Calendars.CONTENT_URI.asSyncAdapter(account),
                values
            )!!.asSyncAdapter(account)
        }
        return uri!!
    }

    private fun verifyOwnerAccountIs(expectedOwnerAccount: String) = provider.use {
        it.query(
            calendarUri,
            arrayOf(Calendars.OWNER_ACCOUNT),
            "${Calendars.ACCOUNT_NAME}=?",
            arrayOf(account.name),
            null
        )!!.use { cursor ->
            cursor.moveToNext()
            val ownerAccount = cursor.getString(0)
            assertEquals(expectedOwnerAccount, ownerAccount)
        }
    }

}