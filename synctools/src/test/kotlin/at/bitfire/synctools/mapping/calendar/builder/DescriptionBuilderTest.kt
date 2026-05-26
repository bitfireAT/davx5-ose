/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Description
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DescriptionBuilderTest {

    private val builder = DescriptionBuilder()

    @Test
    fun `No DESCRIPTION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.DESCRIPTION))
        assertNull(result.entityValues.get(Events.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Description(""))),
            main = VEvent(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.DESCRIPTION))
        assertNull(result.entityValues.get(Events.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Description("Event Details"))),
            main = VEvent(),
            to = result
        )
        assertEquals("Event Details", result.entityValues.getAsString(Events.DESCRIPTION))
    }

}