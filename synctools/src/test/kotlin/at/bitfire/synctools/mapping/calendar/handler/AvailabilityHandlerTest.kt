/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp
import net.fortuna.ical4j.model.property.immutable.ImmutableTransp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class AvailabilityHandlerTest {

    private val handler = AvailabilityHandler()

    @Test
    fun `No availability`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        // OPAQUE is default value
        assertNull(result.transp)
    }

    @Test
    fun `Availability BUSY`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.AVAILABILITY to Events.AVAILABILITY_BUSY
        ))
        handler.process(entity, entity, result)
        // OPAQUE is default value
        assertNull(result.transp)
    }

    @Test
    fun `Availability FREE`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.AVAILABILITY to Events.AVAILABILITY_FREE
        ))
        handler.process(entity, entity, result)
        assertEquals(ImmutableTransp.TRANSPARENT, result.transp)
    }

    @Test
    fun `Availability TENTATIVE`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.AVAILABILITY to Events.AVAILABILITY_TENTATIVE
        ))
        handler.process(entity, entity, result)
        // OPAQUE is default value
        assertNull(result.transp)
    }

}

private val VEvent.transp: Transp?
    get() = getProperty<Transp>(Property.TRANSP).getOrNull()