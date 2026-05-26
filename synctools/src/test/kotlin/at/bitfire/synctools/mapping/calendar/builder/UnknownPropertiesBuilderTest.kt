/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesBuilderTest {

    private val builder = UnknownPropertiesBuilder()

    @Test
    fun `No unknown properties`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `Unknown property with value and parameters`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                XProperty("X-Some-Property", "Some Value")
                    .add<XProperty>(XParameter("Param1", "Value1"))
                    .add(XParameter("Param2", "Value2"))
            )),
            main = VEvent(),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                ExtendedProperties.VALUE to "[\"X-Some-Property\",\"Some Value\",{\"Param1\":\"Value1\",\"Param2\":\"Value2\"}]"
            ),
            result.subValues.first().values
        )
    }


    @Test
    fun unknownProperties() {
        val unknown = XProperty("x-test", "value")
        val result = builder.unknownProperties(VEvent(propertyListOf(
            Uid(),      // processed property
            DtStamp(),  // ignored property
            unknown     // unknown property
        )))
        assertEquals(listOf(unknown), result)
    }

}