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
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
class CategoriesHandlerTest {

    private val handler = CategoriesHandler()

    @Test
    fun `No categories`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        assertEquals(Optional.empty<Categories>(), result.getProperty<Categories>(Property.CATEGORIES))
    }

    @Test
    fun `Multiple categories`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to EventsContract.EXTNAME_CATEGORIES,
            ExtendedProperties.VALUE to "Cat 1\\Cat 2"
        ))
        handler.process(entity, entity, result)
        assertEquals(setOf("Cat 1", "Cat 2"), result.getProperty<Categories>(Property.CATEGORIES).get().categories.texts)
    }

}