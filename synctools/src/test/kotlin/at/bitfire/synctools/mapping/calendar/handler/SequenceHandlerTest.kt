/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceHandlerTest {

    private val handler = SequenceHandler()

    @Test
    fun `No sequence`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        handler.process(entity, entity, result)
        assertNull(result.sequence)
    }

    @Test
    fun `Sequence is 0`() {
        val entity = Entity(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 0
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertNull(result.sequence)
    }

    @Test
    fun `Sequence is 1`() {
        val entity = Entity(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 1
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(1, result.sequence.sequenceNo)
    }

}