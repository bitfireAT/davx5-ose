/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.propertyListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DescriptionBuilderTest {

    private val builder = DescriptionBuilder()

    @Test
    fun `No DESCRIPTION`() {
        val output = Entity(ContentValues())
        val toDo = VToDo()

        builder.build(
            from = toDo,
            main = toDo,
            to = output
        )

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.DESCRIPTION))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION has text`() {
        val output = Entity(ContentValues())
        val toDo = VToDo(propertyListOf(Description("text")))

        builder.build(
            from = toDo,
            main = toDo,
            to = output
        )
        assertEquals("text", output.entityValues.get(JtxContract.JtxICalObject.DESCRIPTION))
    }
}
