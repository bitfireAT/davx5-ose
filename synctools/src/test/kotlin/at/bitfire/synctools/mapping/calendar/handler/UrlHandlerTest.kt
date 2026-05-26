/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class UrlHandlerTest {

    private val handler = UrlHandler()

    @Test
    fun `No URL`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        assertNull(result.url)
    }

    @Test
    fun `Invalid URL`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to EventsContract.EXTNAME_URL,
            ExtendedProperties.VALUE to "invalid\\uri"
        ))
        handler.process(entity, entity, result)
        assertNull(result.url)
    }

    @Test
    fun `Valid URL`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to EventsContract.EXTNAME_URL,
            ExtendedProperties.VALUE to "https://example.com"
        ))
        handler.process(entity, entity, result)
        assertEquals(URI("https://example.com"), result.url.uri)
    }

}