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
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Color
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorBuilderTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @SpyK
    private var builder = ColorBuilder(mockk())

    @Test
    fun `No COLOR`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(
            contentValuesOf(
                Events.EVENT_COLOR_KEY to null,
                Events.EVENT_COLOR to null
            ),
            result.entityValues
        )
    }

    @Test
    fun `COLOR is darkseagreen`() {
        val color = Css3Color.darkseagreen
        every { builder.hasColor(color.name) } returns true

        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Color(null, color.name))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(
            contentValuesOf(
                Events.EVENT_COLOR_KEY to color.name
            ),
            result.entityValues
        )
    }

    @Test
    fun `COLOR is darkseagreen (which is not available)`() {
        val color = Css3Color.darkseagreen
        every { builder.hasColor(color.name) } returns false

        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Color(null, color.name))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(
            contentValuesOf(
                Events.EVENT_COLOR_KEY to null,
                Events.EVENT_COLOR to null
            ),
            result.entityValues
        )
    }

}