/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.Manifest
import android.content.ContentProviderClient
import android.content.Context
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.accounts.toAndroidAccount
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.remote.CalDavCollection
import at.bitfire.davdroid.resource.remote.CalendarQueryFilter
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.Url
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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

    lateinit var accountId: LegacyAccount
    lateinit var providerClient: ContentProviderClient
    lateinit var androidCalendar: AndroidCalendar
    lateinit var localCalendar: LocalCalendar

    @Before
    fun setUp() {
        hiltRule.inject()

        accountId = LegacyAccount(TestAccount.create())
        providerClient = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        // create LocalCalendar
        val androidCalendarProvider = AndroidCalendarProvider(accountId.toAndroidAccount(), providerClient)
        androidCalendar = androidCalendarProvider.createAndGetCalendar(contentValuesOf(
            Calendars.NAME to "Sample Calendar"
        ))
        localCalendar = localCalendarFactory.create(androidCalendar)
    }

    @After
    fun tearDown() {
        localCalendar.androidCalendar.delete()
        providerClient.close()
        TestAccount.remove(accountId.androidAccount)
    }


    @Test
    fun test_generateUpload_existingUid() = runTest {
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
        ), WebDavCollection.Capabilities()
        )

        assertEquals("existing-uid.ics", result.suggestedFileName)

        val iCal = Buffer().also {
            it.write(result.content.toByteArray())
        }.readString(Charsets.UTF_8)
        assertTrue(iCal.contains("UID:existing-uid\r\n"))
    }

    @Test
    fun generateUpload_noUid() = runTest {
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
        ), WebDavCollection.Capabilities()
        )

        assertTrue(result.suggestedFileName.matches(UUID_FILENAME_REGEX))
        val uuid = result.suggestedFileName.removeSuffix(".ics")

        val iCal = Buffer().also {
            it.write(result.content.toByteArray())
        }.readString(Charsets.UTF_8)
        assertTrue(iCal.contains("UID:$uuid\r\n"))

    }


    // helpers

    private fun syncManager() = syncManagerFactory.calendarSyncManager(
        accountId = accountId,
        httpClient = mockk(),
        syncResult = mockk(),
        localCalendar = mockk(),
        collectionInfo = mockk(),
        remoteCollection = CalDavCollection(
            mockk(),
            Url("https://example.com/dav/"),
            CalendarQueryFilter(components = listOf("VEVENT"))
        ),
        resync = mockk(),
        settings = SyncSettingsFixtures.default()
    )


    companion object {

        val UUID_FILENAME_REGEX = "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\\.ics$".toRegex()

    }

}