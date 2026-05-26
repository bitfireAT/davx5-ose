/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class AndroidEventHandlerTest {

    private val handler = AndroidEventHandler(
        accountName = "account@example.com",
        prodIdGenerator = DefaultProdIdGenerator(javaClass.simpleName)
    )

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!


    // mapToVEvents → MappingResult.associatedEvents

    @Test
    fun `mapToVEvents processes exceptions`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring non-all-day event with exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594038600000L,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.ALL_DAY to 0,
                        Events.TITLE to "Event moved to one hour later"
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring non-all-day event with exception", main.summary.value)
        assertEquals(DtStart(dateTimeValue("20200706T193000", tzVienna)), main.dtStart<ZonedDateTime>())
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule<Temporal>>(Property.RRULE).getOrNull()?.value)
        val exception = result.exceptions.first()
        assertEquals(RecurrenceId(dateTimeValue("20200707T193000", tzVienna)), exception.recurrenceId)
        assertEquals(DtStart(dateTimeValue("20200706T203000", tzShanghai)), exception.dtStart<ZonedDateTime>())
        assertEquals("Event moved to one hour later", exception.summary.value)
    }

    @Test
    fun `mapToVEvents ignores exception when there's only one invalid RRULE`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Factically non-recurring non-all-day event with exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;UNTIL=20200706T173000Z"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594038600000L,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.ALL_DAY to 0,
                        Events.TITLE to "Event moved to one hour later"
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Factically non-recurring non-all-day event with exception", main.summary.value)
        assertEquals(DtStart(dateTimeValue("20200706T193000", tzVienna)), main.dtStart<ZonedDateTime>())
        assertTrue(main.getProperties<RRule<*>>(Property.RRULE).isEmpty())
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `mapToVEvents rewrites cancelled exception to EXDATE`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring all-day event with cancelled exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594143000000L,
                        Events.ALL_DAY to 0,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.STATUS to Events.STATUS_CANCELED
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception", main.summary.value)
        assertEquals(DtStart(dateTimeValue("20200706T193000", tzVienna)), main.dtStart<ZonedDateTime>())
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule<*>>(Property.RRULE).getOrNull()?.value)
        assertEquals(
            dateTimeValue("20200707T193000", tzVienna),
            main.getProperty<ExDate<*>>(Property.EXDATE)?.getOrNull()?.dates?.first()
        )
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `mapToVEvents rewrites cancelled all-day exception to EXDATE with VALUE=DATE`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring all-day event with cancelled all-day exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to "UTC",
                    Events.ALL_DAY to 1,    // main event is all-day
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 1,    // exception is all-day
                        Events.DTSTART to 1594143000000L,
                        Events.ALL_DAY to 1,
                        Events.EVENT_TIMEZONE to "UTC",
                        Events.STATUS to Events.STATUS_CANCELED
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled all-day exception", main.summary.value)
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule<*>>(Property.RRULE).get().value)

        // Check that EXDATE has VALUE=DATE
        val exDate = main.getProperty<ExDate<*>>(Property.EXDATE).get()
        assertEquals(Value.DATE, exDate.getParameter<Value>(Parameter.VALUE).get())
        assertEquals(
            dateValue("20200707"),
            main.getProperty<ExDate<*>>(Property.EXDATE)?.getOrNull()?.dates?.first()
        )
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `mapToVEvents rewrites cancelled exception using UTC to EXDATE`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring all-day event with cancelled exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to "UTC",
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594143000000L,
                        Events.ALL_DAY to 0,
                        Events.EVENT_TIMEZONE to "UTC",
                        Events.STATUS to Events.STATUS_CANCELED
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception", main.summary.value)
        assertEquals(DtStart(dateTimeValue("20200706T173000Z")), main.dtStart<Instant>())
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule<*>>(Property.RRULE).getOrNull()?.value)
        assertEquals(
            ExDate<Temporal>("20200707T173000Z"),
            main.getRequiredProperty<ExDate<*>>(Property.EXDATE)
        )
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `mapToVEvents ignores cancelled exception without RECURRENCE-ID`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring all-day event with cancelled exception and no RECURRENCE-ID",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594143000000L,
                        Events.ALL_DAY to 0,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.STATUS to Events.STATUS_CANCELED
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception and no RECURRENCE-ID", main.summary.value)
        assertEquals(DtStart(dateTimeValue("20200706T193000", tzVienna)), main.dtStart<ZonedDateTime>())
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule<*>>(Property.RRULE).getOrNull()?.value)
        assertNull(main.getProperty<ExDate<*>>(Property.EXDATE)?.getOrNull())
        assertTrue(result.exceptions.isEmpty())
    }


    @Test
    fun `mapToVEvents generates DTSTAMP`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )),
                exceptions = emptyList()
            )
        ).associatedEvents
        assertNotNull(result.main?.dateTimeStamp?.date)
    }


    @Test
    fun `mapToVEvents generates PRODID (no packages)`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )),
                exceptions = emptyList()
            )
        ).associatedEvents
        assertEquals(ProdId(javaClass.simpleName), result.prodId)
    }

    @Test
    fun `mapToVEvents generates PRODID (two packages)`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L,
                    Events.MUTATORS to "pkg1,pkg2"
                )),
                exceptions = emptyList()
            )
        ).associatedEvents
        assertEquals(ProdId(javaClass.simpleName), result.prodId)
    }


    // mapToVEvents → MappingResult.uid

    @Test
    fun `mapToVEvents generates UID when necessary`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )),
                exceptions = emptyList()
            )
        )
        assertTrue(result.generatedUid)
        assertNotNull(result.uid)
        assertEquals(result.uid, result.associatedEvents.main?.uid?.getOrNull()?.value)
    }

    @Test
    fun `mapToVEvents takes UID from main event row`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L,
                    Events.UID_2445 to "sample-uid"
                )),
                exceptions = emptyList()
            )
        )
        assertFalse(result.generatedUid)
        assertEquals("sample-uid", result.uid)
        assertEquals("sample-uid", result.associatedEvents.main?.uid?.getOrNull()?.value)
    }

    @Test
    fun `mapToVEvents takes UID from Google Calendar data row`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )).apply {
                    addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
                        ExtendedProperties.NAME to EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,
                        ExtendedProperties.VALUE to "sample-uid"
                    ))
                },
                exceptions = emptyList()
            )
        )
        assertFalse(result.generatedUid)
        assertEquals("sample-uid", result.uid)
        assertEquals("sample-uid", result.associatedEvents.main?.uid?.getOrNull()?.value)
    }

    @Test
    fun `mapToVEvents prefers UID from main event row over Google Calendar data row`() {
        val result = handler.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L,
                    Events.UID_2445 to "sample-uid"
                )).apply {
                    addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
                        ExtendedProperties.NAME to EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,
                        ExtendedProperties.VALUE to "google-calendar"
                    ))
                },
                exceptions = emptyList()
            )
        )
        assertFalse(result.generatedUid)
        assertEquals("sample-uid", result.uid)
        assertEquals("sample-uid", result.associatedEvents.main?.uid?.getOrNull()?.value)
    }

}