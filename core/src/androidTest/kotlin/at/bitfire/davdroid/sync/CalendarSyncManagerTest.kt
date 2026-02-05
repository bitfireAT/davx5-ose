/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CalendarSyncManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionsRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR
    )

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var localCalendarFactory: LocalCalendar.Factory

    @Inject
    lateinit var syncManagerFactory: CalendarSyncManager.Factory

    lateinit var account: Account
    lateinit var providerClient: ContentProviderClient
    lateinit var androidCalendar: AndroidCalendar
    lateinit var localCalendar: LocalCalendar

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()
        providerClient = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        // create LocalCalendar
        val androidCalendarProvider = AndroidCalendarProvider(account, providerClient)
        androidCalendar = androidCalendarProvider.createAndGetCalendar(contentValuesOf(
            Calendars.NAME to "Sample Calendar"
        ))
        localCalendar = localCalendarFactory.create(androidCalendar)
    }

    @After
    fun tearDown() {
        localCalendar.androidCalendar.delete()
        providerClient.closeCompat()
        TestAccount.remove(account)
    }


    @Test
    fun test_generateUpload_existingUid() {
        val result = syncManager().generateUpload(LocalEvent(
            localCalendar.recurringCalendar,
            EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events._ID to 1,
                    Events.CALENDAR_ID to androidCalendar.id,
                    Events.DTSTART to System.currentTimeMillis(),
                    Events.UID_2445 to "existing-uid"
                )),
                exceptions = emptyList()
            )
        ))

        assertEquals("existing-uid.ics", result.suggestedFileName)

        val iCal = Buffer().also {
            result.requestBody.writeTo(it)
        }.readString(Charsets.UTF_8)
        assertTrue(iCal.contains("UID:existing-uid\r\n"))
    }

    @Test
    fun generateUpload_noUid() {
        val result = syncManager().generateUpload(LocalEvent(
            localCalendar.recurringCalendar,
            EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events._ID to 2,
                    Events.CALENDAR_ID to androidCalendar.id,
                    Events.DTSTART to System.currentTimeMillis()
                )),
                exceptions = emptyList()
            )
        ))

        assertTrue(result.suggestedFileName.matches(UUID_FILENAME_REGEX))
        val uuid = result.suggestedFileName.removeSuffix(".ics")

        val iCal = Buffer().also {
            result.requestBody.writeTo(it)
        }.readString(Charsets.UTF_8)
        assertTrue(iCal.contains("UID:$uuid\r\n"))

    }


    // helpers

    private fun syncManager() = syncManagerFactory.calendarSyncManager(
        account = account,
        httpClient = mockk(),
        syncResult = mockk(),
        localCalendar = mockk(),
        collection = mockk(),
        resync = mockk()
    )


    companion object {

        val UUID_FILENAME_REGEX = "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\\.ics$".toRegex()

    }

}