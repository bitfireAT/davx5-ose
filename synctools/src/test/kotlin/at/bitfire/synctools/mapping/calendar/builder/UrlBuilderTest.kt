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
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.calendar.EventsContract
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class UrlBuilderTest {

    private val builder = UrlBuilder()

    @Test
    fun `URL is URI`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Url(URI("https://example.com")))),
            main = VEvent(),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to EventsContract.EXTNAME_URL,
                ExtendedProperties.VALUE to "https://example.com"
            ),
            result.subValues.first().values
        )
    }

    @Test
    fun `No URL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

}