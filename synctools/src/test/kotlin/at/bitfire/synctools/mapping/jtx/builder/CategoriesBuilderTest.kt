/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.property.Categories
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesBuilderTest {

    private val builder = CategoriesBuilder()

    @Test
    fun happyPath() {
        val output = Entity(ContentValues())
        val journal = VJournal().apply {
            this += Categories(TextList("one", "two"))
        }

        builder.build(from = journal, main = journal, output)

        assertEquals(2, output.subValues.size)
        val first = output.subValues[0]
        assertEquals(JtxContract.JtxCategory.CONTENT_URI, first.uri)
        assertEquals("one", first.values.getAsString(JtxContract.JtxCategory.TEXT))
        val second = output.subValues[1]
        assertEquals(JtxContract.JtxCategory.CONTENT_URI, second.uri)
        assertEquals("two", second.values.getAsString(JtxContract.JtxCategory.TEXT))
    }
}
