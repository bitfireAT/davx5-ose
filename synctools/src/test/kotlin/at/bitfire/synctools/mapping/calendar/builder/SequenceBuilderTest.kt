/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.calendar.EventsContract
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Sequence
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceBuilderTest {

    private val builder = SequenceBuilder()

    @Test
    fun `No SEQUENCE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 0
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE is 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Sequence(0))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 0
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE is 1`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(Sequence(1))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 1
        ), result.entityValues)
    }

}