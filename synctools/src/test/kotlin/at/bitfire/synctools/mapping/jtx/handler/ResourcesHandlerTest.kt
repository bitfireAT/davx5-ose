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
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Resources
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class ResourcesHandlerTest {

    private val handler = ResourcesHandler()

    @Test
    fun `No resource sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Resources>(Property.RESOURCES).size)
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            Uri.parse("content://at.techbee.jtx/other"),
            contentValuesOf(JtxContract.JtxResource.TEXT to "should be ignored")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Resources>(Property.RESOURCES).size)
    }

    @Test
    fun `Single resource with text only`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxResource.CONTENT_URI,
            contentValuesOf(JtxContract.JtxResource.TEXT to "conference room")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val resources = output.getProperties<Resources>(Property.RESOURCES)
        assertEquals(1, resources.size)
        assertEquals(setOf("conference room"), resources.first().resources.texts)
    }

    @Test
    fun `Multiple resources`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxResource.CONTENT_URI,
            contentValuesOf(JtxContract.JtxResource.TEXT to "first")
        )
        input.addSubValue(
            JtxContract.JtxResource.CONTENT_URI,
            contentValuesOf(JtxContract.JtxResource.TEXT to "second")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val resources = output.getProperties<Resources>(Property.RESOURCES)
        assertEquals(2, resources.size)
        assertEquals(setOf("first"), resources[0].resources.texts)
        assertEquals(setOf("second"), resources[1].resources.texts)
    }

    @Test
    fun `Single resource with LANGUAGE`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxResource.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxResource.TEXT to "Beamer",
                JtxContract.JtxResource.LANGUAGE to "de"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val resources = output.getProperties<Resources>(Property.RESOURCES)
        assertEquals(1, resources.size)
        assertEquals("Beamer", resources.first().resources.texts.first())
        assertEquals("de", resources.first().getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value)
    }

    @Test
    fun `Single resource with X-parameters in OTHER`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxResource.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxResource.TEXT to "annotated",
                JtxContract.JtxResource.OTHER to """{"X-CUSTOM":"custom-value"}"""
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val resources = output.getProperties<Resources>(Property.RESOURCES)
        assertEquals(1, resources.size)
        assertEquals("annotated", resources.first().resources.texts.first())
        assertEquals(
            "custom-value",
            resources.first().getParameter<XParameter>("X-CUSTOM").getOrNull()?.value
        )
    }

    @Test
    fun `VJOURNAL ignores resource sub-values`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxResource.CONTENT_URI,
            contentValuesOf(JtxContract.JtxResource.TEXT to "conference room")
        )
        val output = VJournal()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Resources>(Property.RESOURCES).size)
    }
}
