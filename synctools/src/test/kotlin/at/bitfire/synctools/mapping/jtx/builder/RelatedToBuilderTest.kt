/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.parameterListOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.RelatedTo
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RelatedToBuilderTest {

    private val builder = RelatedToBuilder()

    @Test
    fun `No RELATED-TO`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertTrue(output.subValues.isEmpty())
    }

    @Test
    fun `single RELATED-TO`() {
        val task = VToDo(
            propertyListOf(
                RelatedTo(parameterListOf(RelType.SIBLING, XParameter("key", "value")), "uid-1")
            )
        )
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertEquals(JtxContract.JtxRelatedto.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to "SIBLING",
                JtxContract.JtxRelatedto.OTHER to """{"key":"value"}"""
            ),
            subValue.values
        )
    }

    @Test
    fun `multiple RELATED-TO`() {
        val task = VToDo(
            propertyListOf(
                RelatedTo(parameterListOf(RelType.SIBLING), "uid-1"),
                RelatedTo(parameterListOf(RelType.CHILD), "uid-2"),
            )
        )
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertEquals(2, output.subValues.size)
        val subValueOne = output.subValues.first { it.values.getAsString(JtxContract.JtxRelatedto.TEXT) == "uid-1" }
        assertEquals(JtxContract.JtxRelatedto.CONTENT_URI, subValueOne.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to "SIBLING",
                JtxContract.JtxRelatedto.OTHER to null
            ),
            subValueOne.values
        )
        val subValueTwo = output.subValues.first { it.values.getAsString(JtxContract.JtxRelatedto.TEXT) == "uid-2" }
        assertEquals(JtxContract.JtxRelatedto.CONTENT_URI, subValueTwo.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-2",
                JtxContract.JtxRelatedto.RELTYPE to "CHILD",
                JtxContract.JtxRelatedto.OTHER to null
            ),
            subValueTwo.values
        )
    }

    @Test
    fun `missing RELTYPE parameter will use PARENT value`() {
        val task = VToDo(propertyListOf(RelatedTo("uid-1")))
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to "PARENT",
                JtxContract.JtxRelatedto.OTHER to null
            ),
            output.subValues.first().values
        )
    }
}
