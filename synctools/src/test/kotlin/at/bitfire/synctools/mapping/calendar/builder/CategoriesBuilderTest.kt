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
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.storage.calendar.EventsContract
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesBuilderTest {

    private val builder = CategoriesBuilder()

    @Test
    fun `Two CATEGORIES (including backslash)`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                this += Categories(TextList("Cat 1", "Cat\\2"))
            },
            main = VEvent(),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to EventsContract.EXTNAME_CATEGORIES,
                ExtendedProperties.VALUE to "Cat 1\\Cat2"
            ),
            result.subValues.first().values
        )
    }

    @Test
    fun `No CATEGORIES`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

}