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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class UidHandlerTest {

    private val handler = UidHandler()

    @Test
    fun `No UID`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        assertNull(result.uid.getOrNull())
    }

    @Test
    fun `UID set`() {
        val entity = Entity(contentValuesOf(
            Events.UID_2445 to "from-event"
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals("from-event", result.uid?.getOrNull()?.value)
    }

}