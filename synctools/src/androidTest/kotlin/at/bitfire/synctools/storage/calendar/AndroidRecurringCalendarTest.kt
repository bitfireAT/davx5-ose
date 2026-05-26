/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.test.assertContentValuesEqual
import at.bitfire.synctools.test.assertEventAndExceptionsEqual
import at.bitfire.synctools.test.withId
import at.bitfire.synctools.verifyCompat
import io.mockk.junit4.MockKRule
import io.mockk.spyk
import net.fortuna.ical4j.util.TimeZones
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class AndroidRecurringCalendarTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        private val testAccount = Account(AndroidRecurringCalendarTest::class.java.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

        private lateinit var client: ContentProviderClient
        private lateinit var calendar: AndroidCalendar
        private lateinit var recurringCalendar: AndroidRecurringCalendar

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

            calendar = TestCalendar.create(testAccount, client)
            recurringCalendar = spyk(AndroidRecurringCalendar(calendar))
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            calendar.delete()
            client.close()
        }

    }

    @get:Rule
    val mockkRule = MockKRule(this)

    @After
    fun cleanUp() {
        // Clean up events after every test
        calendar.deleteAllEvents()
    }
    
    
    // test CRUD

    @Test
    fun testAddEventAndExceptions_and_GetById() {
        // add event and exceptions
        val (mainEventId, event) = insertRecurring()
        val addedWithId = event.withId(mainEventId)

        // verify that cleanUp was called
        verifyCompat (exactly = 1) {
            recurringCalendar.cleanUp(event)
        }

        // verify
        val event2 = recurringCalendar.getById(mainEventId)
        assertEventAndExceptionsEqual(addedWithId, event2!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindEventAndExceptions() {
        val (mainEventId, event) = insertRecurring(syncId = "testFindEventAndExceptions")
        val addedWithId = event.withId(mainEventId)
        val result = recurringCalendar.findEventAndExceptions("${Events._SYNC_ID}=?", arrayOf("testFindEventAndExceptions"))
        assertEventAndExceptionsEqual(addedWithId, result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindEventAndExceptions_NotFound() {
        assertNull(recurringCalendar.findEventAndExceptions("${Events._SYNC_ID}=?", arrayOf("not-existent")))
    }

    @Test
    fun testGetById_NotFound() {
        // make sure there's no event with id=1
        recurringCalendar.deleteEventAndExceptions(1)

        assertNull(recurringCalendar.getById(1))
    }

    @Test
    fun testIterateEventAndExceptions() {
        val (id1, event1) = insertRecurring(syncId = "testIterateEventAndExceptions1")
        val (id2, event2) = insertRecurring(syncId = "testIterateEventAndExceptions2")
        val result = mutableListOf<EventAndExceptions>()
        recurringCalendar.iterateEventAndExceptions(
            "${Events._SYNC_ID} IN (?, ?)",
            arrayOf("testIterateEventAndExceptions1", "testIterateEventAndExceptions2")
        ) { result += it }
        val orderedResult = result.sortedBy { it.main.entityValues.getAsInteger(Events._ID) }
        assertEquals(2, orderedResult.size)
        assertEventAndExceptionsEqual(event1.withId(id1), orderedResult[0], onlyFieldsInExpected = true)
        assertEventAndExceptionsEqual(event2.withId(id2), orderedResult[1], onlyFieldsInExpected = true)
    }

    @Test
    fun testIterateEventAndExceptions_NotFound() {
        recurringCalendar.iterateEventAndExceptions("${Events._SYNC_ID}=?", arrayOf("not-existent")) {
            fail("must not be called")
        }
    }

    @Test
    fun testUpdateEventAndExceptions_NoRebuild() {
        // Create initial event
        val now = 1754233504000     // Sun Aug 03 2025 15:05:04 GMT+0000
        val initialEvent = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to "recur2",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZones.GMT_ID,
            Events.DURATION to "PT1H",
            Events.TITLE to "Initial Event",
            Events.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val initialExceptions = listOf(
            Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events.ORIGINAL_SYNC_ID to "recur2",
                Events.DTSTART to now + 86400000,
                Events.DTEND to now + 86400000 + 2*3600000,
                Events.TITLE to "Initial Exception"
            ))
        )
        val initialEventAndExceptions = EventAndExceptions(main = initialEvent, exceptions = initialExceptions)

        // Add initial event
        val addedEventId = recurringCalendar.addEventAndExceptions(initialEventAndExceptions)

        // Create updated event (no rebuild needed)
        val updatedEvent = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to "recur2",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZones.GMT_ID,
            Events.DURATION to "PT1H",
            Events.TITLE to "Updated Event",
            Events.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val updatedExceptions = listOf(
            Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events.ORIGINAL_SYNC_ID to "recur2",
                Events.DTSTART to now + 86400000,
                Events.DTEND to now + 86400000 + 2*3600000,
                Events.TITLE to "Updated Exception"
            ))
        )
        val updatedEventAndExceptions = EventAndExceptions(main = updatedEvent, exceptions = updatedExceptions)

        // Update event
        val updatedEventId = recurringCalendar.updateEventAndExceptions(addedEventId, updatedEventAndExceptions)
        assertEquals(updatedEventId, addedEventId)

        // verify that cleanUp was called
        verifyCompat(exactly = 1) {
            recurringCalendar.cleanUp(updatedEventAndExceptions)
        }

        // Verify update
        val event2 = recurringCalendar.getById(addedEventId)
        assertEventAndExceptionsEqual(
            updatedEventAndExceptions.withId(addedEventId),
            event2!!,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun testUpdateEventAndExceptions_RebuildNeeded() {
        // Add initial event with STATUS
        val now = 1754233504000     // Sun Aug 03 2025 15:05:04 GMT+0000
        val initialEvent = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to "recur3",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZones.GMT_ID,
            Events.DURATION to "PT1H",
            Events.TITLE to "Initial Event",
            Events.STATUS to Events.STATUS_CONFIRMED,
            Events.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val initialExceptions = listOf(
            Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events.ORIGINAL_SYNC_ID to "recur3",
                Events.DTSTART to now + 86400000,
                Events.DTEND to now + 86400000 + 2*3600000,
                Events.TITLE to "Initial Exception",
                Events.STATUS to Events.STATUS_CONFIRMED
            ))
        )
        val initialEventAndExceptions = EventAndExceptions(main = initialEvent, exceptions = initialExceptions)
        val mainEventId = recurringCalendar.addEventAndExceptions(initialEventAndExceptions)

        // Create updated event with null STATUS (requires rebuild)
        val updatedEvent = Entity(initialEvent.entityValues.apply {
            put(Events.TITLE, "Updated Event")
            putNull(Events.STATUS)
        })
        val updatedEventAndExceptions = EventAndExceptions(main = updatedEvent, exceptions = initialExceptions)

        // Update event (should trigger rebuild)
        val updatedEventId = recurringCalendar.updateEventAndExceptions(mainEventId, updatedEventAndExceptions)
        assertNotEquals(mainEventId, updatedEventId)

        // Verify update = deletion + re-creation
        assertNull(recurringCalendar.getById(mainEventId))
        val updatedEvent2 = recurringCalendar.getById(updatedEventId)!!
        if (!updatedEvent2.main.entityValues.containsKey(Events.STATUS))      // STATUS will not be returned if it's null
            updatedEvent2.main.entityValues.putNull(Events.STATUS)      // add for equality check
        assertEventAndExceptionsEqual(
            updatedEventAndExceptions.withId(updatedEventId),
            updatedEvent2,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun testDeleteEventAndExceptions() {
        // Add event with exceptions
        val now = 1754233504000     // Sun Aug 03 2025 15:05:04 GMT+0000
        val mainEvent = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to "recur4",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZones.GMT_ID,
            Events.DURATION to "PT1H",
            Events.TITLE to "Main Event",
            Events.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val exceptions = listOf(
            Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events.ORIGINAL_SYNC_ID to "recur4",
                Events.DTSTART to now + 86400000,
                Events.DTEND to now + 86400000 + 2*3600000,
                Events.TITLE to "Exception"
            ))
        )
        val mainEventId = recurringCalendar.addEventAndExceptions(EventAndExceptions(main = mainEvent, exceptions = exceptions))

        // Delete event and exceptions
        recurringCalendar.deleteEventAndExceptions(mainEventId)

        // Verify deletion
        val deletedEvent = recurringCalendar.getById(mainEventId)
        assertNull(deletedEvent)
    }

    
    // test validation / clean-up logic

    @Test
    fun testCleanUp_Recurring_Exceptions_NoSyncId() {
        val cleaned = recurringCalendar.cleanUp(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events.TITLE to "Recurring Main Event",
                Events.RRULE to "Some RRULE"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    Events.TITLE to "Exception"
                ))
            )
        ))

        // verify that exceptions were dropped (because the provider wouldn't be able to associate them without SYNC_ID)
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanUp_Recurring_Exceptions_WithSyncId() {
        val original = EventAndExceptions(
            main = Entity(contentValuesOf(
                Events._SYNC_ID to "SomeSyncId",
                Events.TITLE to "Recurring Main Event",
                Events.RRULE to "Some RRULE"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    Events.TITLE to "Exception",
                    Events.ORIGINAL_SYNC_ID to "SomeSyncId"
                ))
            )
        )
        val cleaned = recurringCalendar.cleanUp(original)

        // verify that cleanUp didn't modify anything
        assertEventAndExceptionsEqual(original, cleaned)
    }

    @Test
    fun testCleanUp_NotRecurring_Exceptions() {
        val cleaned = recurringCalendar.cleanUp(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events._SYNC_ID to "SomeSyncID",
                Events.TITLE to "Non-Recurring Main Event"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    Events.TITLE to "Exception"
                ))
            )
        ))

        // verify that exceptions were dropped (because the main event is not recurring)
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanMainEvent_RemovesOriginalFields() {
        val result = recurringCalendar.cleanMainEvent(Entity(contentValuesOf(
            Events.ORIGINAL_ID to "SomeValue",
            Events.ORIGINAL_SYNC_ID to "SomeValue",
            Events.ORIGINAL_INSTANCE_TIME to "SomeValue",
            Events.ORIGINAL_ALL_DAY to "SomeValue"
        )))
        assertEquals(0, result.entityValues.size())
    }

    @Test
    fun testCleanException_RemovesRecurrenceFields_AddsSyncId() {
        val result = recurringCalendar.cleanException(Entity(contentValuesOf(
            Events.RRULE to "SomeValue",
            Events.RDATE to "SomeValue",
            Events.EXRULE to "SomeValue",
            Events.EXDATE to "SomeValue"
        )), "SomeSyncID")

        // all fields should have been dropped, but ORIGINAL_SYNC_ID should have been added
        assertContentValuesEqual(
            contentValuesOf(Events.ORIGINAL_SYNC_ID to "SomeSyncID"),
            result.entityValues
        )
    }

    
    // test processing dirty/deleted events and exceptions

    @Test
    fun testProcessDeletedExceptions() {
        val now = System.currentTimeMillis()
        val mainValues = contentValuesOf(
            Events._SYNC_ID to "testProcessDeletedExceptions",
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DURATION to "PT1H",
            Events.RRULE to "FREQ=DAILY;COUNT=5",
            Events.DIRTY to 0,
            Events.DELETED to 0,
            EventsContract.COLUMN_SEQUENCE to 15
        )
        val exNotDeleted = Entity(
            contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events.ORIGINAL_INSTANCE_TIME to now,
                Events.ORIGINAL_ALL_DAY to 0,
                Events.DTSTART to now,
                Events.TITLE to "not marked as deleted",
                Events.DIRTY to 0,
                Events.DELETED to 0
            )
        )
        val mainId = recurringCalendar.addEventAndExceptions(
            EventAndExceptions(
                main = Entity(mainValues),
                exceptions = listOf(
                    exNotDeleted,
                    Entity(
                        contentValuesOf(
                            Events.CALENDAR_ID to calendar.id,
                            Events.ORIGINAL_INSTANCE_TIME to now,
                            Events.ORIGINAL_ALL_DAY to 0,
                            Events.DTSTART to now,
                            Events.DIRTY to 1,
                            Events.DELETED to 1,
                            Events.TITLE to "marked as deleted"
                        )
                    )
                )
            )
        )

        // should update main event and purge the deleted exception
        recurringCalendar.processDeletedExceptions()

        val result = recurringCalendar.getById(mainId)!!
        assertEventAndExceptionsEqual(
            EventAndExceptions(
                main = Entity(ContentValues(mainValues).apply {
                    put(Events.DIRTY, 1)
                    put(EventsContract.COLUMN_SEQUENCE, 16)
                }),
                exceptions = listOf(exNotDeleted)
            ), result, onlyFieldsInExpected = true
        )
    }

    @Test
    fun testProcessDirtyExceptions() {
        val now = System.currentTimeMillis()
        val mainValues = contentValuesOf(
            Events._SYNC_ID to "testProcessDirtyExceptions",
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DURATION to "PT1H",
            Events.RRULE to "FREQ=DAILY;COUNT=5",
            Events.DIRTY to 0,
            Events.DELETED to 0,
            EventsContract.COLUMN_SEQUENCE to 15
        )
        val exDirtyValues = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_INSTANCE_TIME to now,
            Events.ORIGINAL_ALL_DAY to 0,
            Events.DTSTART to now,
            Events.DIRTY to 1,
            Events.DELETED to 0,
            Events.TITLE to "marked as dirty",
            EventsContract.COLUMN_SEQUENCE to null
        )
        val mainId = recurringCalendar.addEventAndExceptions(
            EventAndExceptions(
                main = Entity(mainValues),
                exceptions = listOf(Entity(exDirtyValues))
            )
        )

        // should mark main event as dirty and increase exception SEQUENCE
        recurringCalendar.processDirtyExceptions()

        val result = recurringCalendar.getById(mainId)!!
        assertEventAndExceptionsEqual(
            EventAndExceptions(
                main = Entity(ContentValues(mainValues).apply {
                    put(Events.DIRTY, 1)
                }),
                exceptions = listOf(Entity(ContentValues(exDirtyValues).apply {
                    put(Events.DIRTY, 0)
                    put(EventsContract.COLUMN_SEQUENCE, 1)
                }))
            ), result, onlyFieldsInExpected = true
        )
    }


    // helpers

    private fun insertRecurring(syncId: String = UUID.randomUUID().toString()): Pair<Long, EventAndExceptions> {
        val now = 1754233504000     // Sun Aug 03 2025 15:05:04 GMT+0000
        val mainEvent = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to syncId,
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZones.GMT_ID,
            Events.DURATION to "PT1H",
            Events.TITLE to "Main Event",
            Events.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val event = EventAndExceptions(
            main = mainEvent,
            exceptions = listOf(
                Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events.DTSTART to now + 86400000,
                    Events.DTEND to now + 86400000 + 2*3600000,
                    Events.TITLE to "Exception"
                ))
            )
        )
        val id = recurringCalendar.addEventAndExceptions(event)
        return id to event
    }

}