/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.RelatedTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RelatedToHandlerTest {

    private val handler = RelatedToHandler()

    @Test
    fun `No related-to sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<RelatedTo>(Property.RELATED_TO).size)
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            Uri.parse("content://at.techbee.jtx/other"),
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to JtxContract.JtxRelatedto.Reltype.PARENT.name
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<RelatedTo>(Property.RELATED_TO).size)
    }

    @Test
    fun `Single related-to`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to JtxContract.JtxRelatedto.Reltype.PARENT.name
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val relatedTo = output.getProperties<RelatedTo>(Property.RELATED_TO).single()
        assertEquals("uid-1", relatedTo.value)
        assertEquals(RelType.PARENT, relatedTo.getParameter<RelType>(Parameter.RELTYPE).getOrNull())
    }

    @Test
    fun `Multiple related-to`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to JtxContract.JtxRelatedto.Reltype.CHILD.name
            )
        )
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-2",
                JtxContract.JtxRelatedto.RELTYPE to JtxContract.JtxRelatedto.Reltype.SIBLING.name
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val relatedTo = output.getProperties<RelatedTo>(Property.RELATED_TO)
        assertEquals(2, relatedTo.size)
        assertEquals("uid-1", relatedTo[0].value)
        assertEquals(RelType.CHILD, relatedTo[0].getParameter<RelType>(Parameter.RELTYPE).getOrNull())
        assertEquals("uid-2", relatedTo[1].value)
        assertEquals(RelType.SIBLING, relatedTo[1].getParameter<RelType>(Parameter.RELTYPE).getOrNull())
    }

    @Test
    fun `Related-to with X-parameters in OTHER`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to JtxContract.JtxRelatedto.Reltype.PARENT.name,
                JtxContract.JtxRelatedto.OTHER to """{"X-CUSTOM":"custom-value"}"""
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val relatedTo = output.getProperties<RelatedTo>(Property.RELATED_TO).single()
        assertEquals("uid-1", relatedTo.value)
        assertEquals("custom-value", relatedTo.getParameter<XParameter>("X-CUSTOM").getOrNull()?.value)
    }

    @Test
    fun `Related-to without TEXT is mapped`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(JtxContract.JtxRelatedto.RELTYPE to JtxContract.JtxRelatedto.Reltype.PARENT.name)
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val relatedTo = output.getProperties<RelatedTo>(Property.RELATED_TO).single()
        assertNull(relatedTo.value)
        assertEquals(RelType.PARENT, relatedTo.getParameter<RelType>(Parameter.RELTYPE).getOrNull())
    }

    @Test
    fun `Related-to without RELTYPE is skipped`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(JtxContract.JtxRelatedto.TEXT to "uid-1")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<RelatedTo>(Property.RELATED_TO).size)
    }

    @Test
    fun `Related-to with unsupported RELTYPE is skipped`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxRelatedto.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxRelatedto.TEXT to "uid-1",
                JtxContract.JtxRelatedto.RELTYPE to "unsupported"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<RelatedTo>(Property.RELATED_TO).size)
    }
}
