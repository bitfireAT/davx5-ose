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
import net.fortuna.ical4j.model.property.immutable.ImmutableTransp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvailabilityBuilderTest {

    private val builder = AvailabilityBuilder()

    @Test
    fun `No transparency`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.AVAILABILITY))
        assertEquals(Events.AVAILABILITY_BUSY, result.entityValues.get(Events.AVAILABILITY))
    }

    @Test
    fun `Transparency is OPAQUE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(ImmutableTransp.OPAQUE)),
            main = VEvent(),
            to = result
        )
        assertEquals(Events.AVAILABILITY_BUSY, result.entityValues.get(Events.AVAILABILITY))
    }

    @Test
    fun `Transparency is TRANSPARENT`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(ImmutableTransp.TRANSPARENT)),
            main = VEvent(),
            to = result
        )
        assertEquals(Events.AVAILABILITY_FREE, result.entityValues.get(Events.AVAILABILITY))
    }

}