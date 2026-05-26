/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DescriptionHandlerTest {

    private val handler = DescriptionHandler()

    @Test
    fun `No description`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.description)
    }

    @Test
    fun `Description with text`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.DESCRIPTION to "text"))
        val output = VToDo()

        handler.process(input, input, output)

        assertEquals(Description("text"), output.description)
    }
}
