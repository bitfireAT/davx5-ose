/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
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
        val main = VJournal()

        builder.build(from = journal, main = main, output)

        assertEquals(2, output.subValues.size)
        val first = output.subValues[0]
        assertEquals(JtxContract.JtxCategory.CONTENT_URI, first.uri)
        assertEquals("one", first.values.getAsString(JtxContract.JtxCategory.TEXT))
        val second = output.subValues[1]
        assertEquals(JtxContract.JtxCategory.CONTENT_URI, second.uri)
        assertEquals("two", second.values.getAsString(JtxContract.JtxCategory.TEXT))
    }
}
