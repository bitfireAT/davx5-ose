/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.ical4android.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.property.DtStart
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

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncManagerFactory: CalendarSyncManager.Factory

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()
        account = TestAccount.create()
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    @Test
    fun generateUpload_existingUid() {
        val result = syncManager().generateUpload(mockk(relaxed = true) {
            every { getCachedEvent() } returns Event(uid = "existing-uid", dtStart = DtStart())
        })

        assertEquals("existing-uid.ics", result.suggestedFileName)
        assertTrue(result.onSuccessContext.uid.isEmpty)

        val iCal = Buffer().also {
            result.requestBody.writeTo(it)
        }.readString(Charsets.UTF_8)
        assertTrue(iCal.contains("UID:existing-uid\r\n"))
    }

    @Test
    fun generateUpload_noUid() {
        val result = syncManager().generateUpload(mockk(relaxed = true) {
            every { getCachedEvent() } returns Event(dtStart = DtStart())
        })

        assertTrue(result.suggestedFileName.matches(UUID_FILENAME_REGEX))
        val uuid = result.suggestedFileName.removeSuffix(".ics")

        assertEquals(uuid, result.onSuccessContext.uid.get())

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