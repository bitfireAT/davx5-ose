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
import net.fortuna.ical4j.model.property.Summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleBuilderTest {

    private val builder = TitleBuilder()

    @Test
    fun `No SUMMARY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.TITLE))
        assertNull(result.entityValues.get(Events.TITLE))
    }

    @Test
    fun `SUMMARY is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Summary(""))),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.TITLE))
        assertNull(result.entityValues.get(Events.TITLE))
    }

    @Test
    fun `SUMMARY is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Summary("Event Summary"))),
            main = VEvent(),
            to = result
        )
        assertEquals("Event Summary", result.entityValues.getAsString(Events.TITLE))
    }

}