/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class ColorHandlerTest {

    private val handler = ColorHandler()

    @Test
    fun `No color`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        assertNull(result.getProperty<Color>(Color.PROPERTY_NAME).getOrNull())
    }

    @Test
    fun `Color from index`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.EVENT_COLOR_KEY to Css3Color.silver.name
        ))
        handler.process(entity, entity, result)
        assertEquals("silver", result.getProperty<Color>(Color.PROPERTY_NAME)?.getOrNull()?.value)
    }

    @Test
    fun `Color from value`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.EVENT_COLOR to Css3Color.silver.argb
        ))
        handler.process(entity, entity, result)
        assertEquals("silver", result.getProperty<Color>(Color.PROPERTY_NAME)?.getOrNull()?.value)
    }

}