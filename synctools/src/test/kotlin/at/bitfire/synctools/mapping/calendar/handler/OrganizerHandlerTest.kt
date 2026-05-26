/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerHandlerTest {

    private val handler = OrganizerHandler()

    @Test
    fun `ORGANIZER added to event with attendees`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "organizer@example.com",
            Attendees.ATTENDEE_TYPE to Attendees.RELATIONSHIP_ORGANIZER
        ))
        handler.process(entity, entity, result)
        assertEquals(Organizer("mailto:organizer@example.com"), result.organizer)
    }

    @Test
    fun `ORGANIZER must be same in exception without attendees`() {
        // RFC 6638 3.2.4.2: All the calendar components in a scheduling object resource MUST
        // contain the same "ORGANIZER" property value when present.

        // This test verifies that when the main event has attendees and ORGANIZER, the exception
        // must also have the same ORGANIZER, even if the exception has no attendees.

        val mainEntity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        mainEntity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "organizer@example.com",
            Attendees.ATTENDEE_TYPE to Attendees.RELATIONSHIP_ORGANIZER
        ))

        // Exception has no attendees
        val exceptionEntity = Entity(contentValuesOf())

        // Process main event
        val mainVEvent = VEvent()
        handler.process(mainEntity, mainEntity, mainVEvent)
        assertEquals(Organizer("mailto:organizer@example.com"), mainVEvent.organizer)

        // Process exception - should still get ORGANIZER from main event
        val exceptionVEvent = VEvent()
        handler.process(exceptionEntity, mainEntity, exceptionVEvent)
        assertEquals(Organizer("mailto:organizer@example.com"), exceptionVEvent.organizer)
    }
    @Test
    fun `No ORGANIZER in exception when main has no attendees`() {
        // If main event has no attendees, no ORGANIZER should be added
        val mainEntity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        // No attendees subValues

        val exceptionEntity = Entity(contentValuesOf())

        val mainVEvent = VEvent()
        handler.process(mainEntity, mainEntity, mainVEvent)
        assertNull(mainVEvent.organizer)

        val exceptionVEvent = VEvent()
        handler.process(exceptionEntity, mainEntity, exceptionVEvent)
        assertNull(exceptionVEvent.organizer)
    }

    @Test
    fun `ORGANIZER in exception when exception has attendees but main does not`() {
        val mainEntity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        val exceptionEntity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        exceptionEntity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "organizer@example.com",
            Attendees.ATTENDEE_TYPE to Attendees.RELATIONSHIP_ORGANIZER
        ))
        val mainVEvent = VEvent()
        handler.process(mainEntity, mainEntity, mainVEvent)
        assertNull(mainVEvent.organizer)

        val exceptionVEvent = VEvent()
        handler.process(exceptionEntity, mainEntity, exceptionVEvent)
        assertEquals(Organizer("mailto:organizer@example.com"), exceptionVEvent.organizer)
    }

    @Test
    fun `ORGANIZER is preserved when attendees exist`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))

        handler.process(entity, entity, result)

        assertEquals(
            Organizer("mailto:organizer@example.com"),
            result.organizer
        )
    }

    @Test
    fun `Exception ORGANIZER replaced with main ORGANIZER`() {
        val mainEntity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        mainEntity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))

        val exceptionEntity = Entity(contentValuesOf(
            Events.ORGANIZER to "other@example.com"
        ))

        val exceptionVEvent = VEvent()
        handler.process(exceptionEntity, mainEntity, exceptionVEvent)

        // override with main
        assertEquals(
            Organizer("mailto:organizer@example.com"),
            exceptionVEvent.organizer
        )
    }

    @Test
    fun `No ORGANIZER anywhere results in no ORGANIZER`() {
        val mainEntity = Entity(contentValuesOf())
        val exceptionEntity = Entity(contentValuesOf())

        val mainVEvent = VEvent()
        handler.process(mainEntity, mainEntity, mainVEvent)
        assertNull(mainVEvent.organizer)

        val exceptionVEvent = VEvent()
        handler.process(exceptionEntity, mainEntity, exceptionVEvent)
        assertNull(exceptionVEvent.organizer)
    }

}