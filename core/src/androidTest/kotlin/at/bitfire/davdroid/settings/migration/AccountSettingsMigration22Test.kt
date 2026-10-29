/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.Manifest
import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.local.LocalCalendarStore
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.synctools.storage.LocalStorageClient
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration22Test {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var migration: AccountSettingsMigration22

    @Inject
    lateinit var calendarStore: LocalCalendarStore

    private lateinit var account: Account
    private lateinit var client: LocalStorageClient
    private lateinit var provider: AndroidCalendarProvider

    @Before
    fun setUp() {
        hiltRule.inject()
        account = TestAccount.create()
        client = LocalStorageClient(context.contentResolver.acquireContentProviderClient(Calendars.CONTENT_URI.authority!!)!!)
        provider = AndroidCalendarProvider(account, client)
    }

    @After
    fun tearDown() {
        for (calendar in provider.findCalendars())
            provider.deleteCalendar(calendar.id)
        client.close()
        TestAccount.remove(account)
    }


    @Test
    fun testMigrateCalendars_MovesLegacySyncState() {
        val syncState = """{"type":"CTAG","value":"ctag-123"}"""
        val id = createCalendar(url = syncState)

        migration.migrateCalendars(account)

        assertNull(read(id, Calendars.CAL_SYNC1))
        assertEquals(syncState, read(id, Calendars.CAL_SYNC2))
        assertEquals(syncState, provider.readCalendarSyncState(id))
    }

    @Test
    fun testMigrateCalendars_NoLegacySyncState() {
        val id = createCalendar(url = null)

        migration.migrateCalendars(account)

        assertNull(read(id, Calendars.CAL_SYNC1))
        assertNull(read(id, Calendars.CAL_SYNC2))
    }

    @Test
    fun testMigrateCalendars_LeavesCollectionUrlUntouched() {
        val url = "https://example.com/caldav/calendar/"
        val id = createCalendar(url = url)

        migration.migrateCalendars(account)

        assertEquals(url, read(id, Calendars.CAL_SYNC1))
        assertNull(read(id, Calendars.CAL_SYNC2))
    }

    @Test
    fun testMigrateCalendars_MultipleCalendars() {
        val state1 = """{"type":"CTAG","value":"ctag-1"}"""
        val state2 = """{"type":"SYNC_TOKEN","value":"token-2"}"""
        val id1 = createCalendar(url = state1, syncId = 1)
        val id2 = createCalendar(url = state2, syncId = 2)
        val id3 = createCalendar(url = null, syncId = 3)

        migration.migrateCalendars(account)

        assertEquals(state1, provider.readCalendarSyncState(id1))
        assertEquals(state2, provider.readCalendarSyncState(id2))
        assertNull(provider.readCalendarSyncState(id3))
    }

    @Test
    fun testMigrateCalendars_IsIdempotent() {
        val syncState = """{"type":"CTAG","value":"ctag-123"}"""
        val id = createCalendar(url = syncState)

        migration.migrateCalendars(account)
        migration.migrateCalendars(account)

        assertEquals(syncState, provider.readCalendarSyncState(id))
    }


    // helpers

    private fun createCalendar(url: String?, syncId: Long = 1): Long =
        provider.createCalendar(contentValuesOf(
            Calendars.OWNER_ACCOUNT to account.name,
            Calendars.VISIBLE to 1,
            Calendars.SYNC_EVENTS to 1,
            Calendars._SYNC_ID to syncId,
            Calendars.CALENDAR_DISPLAY_NAME to "Calendar $syncId",
            Calendars.CAL_SYNC1 to url
        ))

    private fun read(id: Long, column: String): String? =
        client.query(provider.calendarUri(id), arrayOf(column))!!.use { cursor ->
            if (cursor.moveToNext()) cursor.getString(0) else null
        }

}
