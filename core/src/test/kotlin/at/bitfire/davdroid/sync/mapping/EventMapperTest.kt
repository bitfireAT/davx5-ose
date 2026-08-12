/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import android.accounts.Account
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import io.ktor.client.engine.mock.toByteArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class EventMapperTest {

    private val productIds = ProductIds(RuntimeEnvironment.getApplication())
    private val mapper = EventMapper(
        logger = Logger.getLogger(javaClass.name),
        productIds = productIds
    )

    @Test
    fun `generateUpload() keeps existing UID`() = runTest {
        val resource = localEvent(
            EventAndExceptions(
                main = Entity(
                    contentValuesOf(
                        Events.DTSTART to 1594056600000L,
                        Events.UID_2445 to "existing-uid"
                    )
                ),
                exceptions = emptyList()
            )
        )

        val result = mapper.generateUpload(resource, WebDavCollection.Capabilities())

        assertEquals("existing-uid.ics", result.suggestedFileName)
        assertTrue(iCal(result).contains("UID:existing-uid\r\n"))
    }

    @Test
    fun `generateUpload() generates and persists UID if missing`() = runTest {
        val resource = localEvent(
            EventAndExceptions(
                main = Entity(
                    contentValuesOf(
                        Events.DTSTART to 1594056600000L
                    )
                ),
                exceptions = emptyList()
            )
        )

        val result = mapper.generateUpload(resource, WebDavCollection.Capabilities())

        val uuid = result.suggestedFileName.removeSuffix(".ics")
        assertTrue(result.suggestedFileName.matches(UUID_FILENAME_REGEX))
        assertTrue(iCal(result).contains("UID:$uuid\r\n"))
        verify(exactly = 1) { resource.updateUid(uuid) }
    }


    // helpers

    private fun localEvent(eventAndExceptions: EventAndExceptions) = mockk<LocalEvent>(relaxed = true) {
        every { androidEvent } returns eventAndExceptions
        every { recurringCalendar } returns mockk<AndroidRecurringCalendar>(relaxed = true) {
            every { calendar } returns mockk<AndroidCalendar>(relaxed = true) {
                every { account } returns Account("test@example.com", "test")
            }
        }
    }

    private suspend fun iCal(result: GeneratedResource) =
        Buffer().also { it.write(result.content.toByteArray()) }.readString(Charsets.UTF_8)


    companion object {
        val UUID_FILENAME_REGEX =
            "^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\\.ics$".toRegex()
    }

}
