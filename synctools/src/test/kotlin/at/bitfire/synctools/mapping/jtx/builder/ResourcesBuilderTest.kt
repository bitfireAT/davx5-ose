/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Resources
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourcesBuilderTest {

    private val builder = ResourcesBuilder()

    @Test
    fun `VTODO without resources`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, output)

        assertEquals(0, output.subValues.size)
    }

    @Test
    fun `VTODO with single resource`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Resources("resource")
        }
        val main = VToDo()

        builder.build(from = task, main = main, output)

        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertEquals(JtxContract.JtxResource.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxResource.TEXT to "resource",
                JtxContract.JtxResource.ID to 0L,
                JtxContract.JtxResource.LANGUAGE to null,
                JtxContract.JtxResource.OTHER to null
            ),
            actual = subValue.values
        )
    }

    @Test
    fun `VTODO with multiple resources`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Resources(listOf("one", "two"))
            this += Resources("three")
        }

        builder.build(from = task, main = task, output)

        assertEquals(3, output.subValues.size)
        val first = output.subValues[0]
        assertEquals(JtxContract.JtxResource.CONTENT_URI, first.uri)
        assertEquals("one", first.values.getAsString(JtxContract.JtxResource.TEXT))
        val second = output.subValues[1]
        assertEquals(JtxContract.JtxResource.CONTENT_URI, second.uri)
        assertEquals("two", second.values.getAsString(JtxContract.JtxResource.TEXT))
        val third = output.subValues[2]
        assertEquals(JtxContract.JtxResource.CONTENT_URI, third.uri)
        assertEquals("three", third.values.getAsString(JtxContract.JtxResource.TEXT))
    }

    @Test
    fun `VJOURNAL should ignore RESOURCES properties`() {
        val output = Entity(ContentValues())
        val journal = VJournal().apply {
            this += Resources(listOf("one", "two"))
        }

        builder.build(from = journal, main = journal, output)

        assertEquals(0, output.subValues.size)
    }
}
