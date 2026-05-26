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
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusHandlerTest {

    private val handler = StatusHandler()

    @Test
    fun `No status`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        assertNull(result.status)
    }

    @Test
    fun `Status CONFIRMED`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.STATUS to Events.STATUS_CONFIRMED
        ))
        handler.process(entity, entity, result)
        assertEquals(ImmutableStatus.VEVENT_CONFIRMED, result.status)
    }

    @Test
    fun `Status TENTATIVE`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.STATUS to Events.STATUS_TENTATIVE
        ))
        handler.process(entity, entity, result)
        assertEquals(ImmutableStatus.VEVENT_TENTATIVE, result.status)
    }

    @Test
    fun `Status CANCELLED`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.STATUS to Events.STATUS_CANCELED
        ))
        handler.process(entity, entity, result)
        assertEquals(ImmutableStatus.VEVENT_CANCELLED, result.status)
    }

}