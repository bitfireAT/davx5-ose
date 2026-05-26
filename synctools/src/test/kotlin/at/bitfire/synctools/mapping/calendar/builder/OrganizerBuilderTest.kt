/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerBuilderTest {

    private val ownerAccount = "owner@example.com"
    private val builder = OrganizerBuilder(ownerAccount)

    @Test
    fun `Event is not group-scheduled`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Organizer("mailto:organizer@example.com"))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORGANIZER to ownerAccount,
            Events.HAS_ATTENDEE_DATA to 0
        ), result.entityValues)
    }

    @Test
    fun `ORGANIZER is email address`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Organizer("mailto:organizer@example.com"))).apply {
                // at least one attendee to make event group-scheduled
                add<VEvent>(Attendee("mailto:attendee@example.com"))
            },
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com",
            Events.HAS_ATTENDEE_DATA to 1
        ), result.entityValues)
    }

    @Test
    fun `ORGANIZER is custom URI`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Organizer("local-id:user"))).apply {
                // at least one attendee to make event group-scheduled
                add<VEvent>(Attendee("mailto:attendee@example.com"))
            },
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORGANIZER to ownerAccount,
            Events.HAS_ATTENDEE_DATA to 1
        ), result.entityValues)
    }

    @Test
    fun `ORGANIZER is custom URI with email parameter`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                Organizer("local-id:user")
                    .add(Email("organizer@example.com")),
                Attendee("mailto:attendee@example.com")
            )),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com",
            Events.HAS_ATTENDEE_DATA to 1
        ), result.entityValues)
    }

}