/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationBuilderTest {

    private val builder = LocationBuilder()

    @Test
    fun `No LOCATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.EVENT_LOCATION))
        assertNull(result.entityValues.get(Events.EVENT_LOCATION))
    }

    @Test
    fun `LOCATION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Location(""))),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.EVENT_LOCATION))
        assertNull(result.entityValues.get(Events.EVENT_LOCATION))
    }

    @Test
    fun `LOCATION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Location("Event Location"))),
            main = VEvent(),
            to = result
        )
        assertEquals("Event Location", result.entityValues.getAsString(Events.EVENT_LOCATION))
    }

}