/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import androidx.core.content.contentValuesOf
import at.bitfire.parameterListOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attach
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AttachmentsBuilderTest {

    private val builder = AttachmentsBuilder()

    @Test
    fun `No ATTACH property`() {
        val task = VToDo()

        val result = builder.build(task)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `ATTACH with URL`() {
        val task = VToDo(
            propertyListOf(
                Attach(
                    parameterListOf(
                        FmtType("application/octet-stream"),
                        XParameter("FILENAME", "file.ext"),
                        XParameter("key", "value")
                    ),
                    URI.create("https://domain.example/file.ext")
                )
            )
        )

        val result = builder.build(task)

        assertEquals(1, result.size)
        val subValue = result.first()
        assertEquals(JtxContract.JtxAttachment.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxAttachment.URI to "https://domain.example/file.ext",
                JtxContract.JtxAttachment.FMTTYPE to "application/octet-stream",
                JtxContract.JtxAttachment.FILENAME to "file.ext",
                JtxContract.JtxAttachment.OTHER to """{"key":"value"}"""
            ),
            subValue.values
        )
        assertNull(subValue.binaryData)
    }

    @Test
    fun `ATTACH with inline content`() {
        val binary = "Hello world".toByteArray()
        val task = VToDo(
            propertyListOf(
                Attach(
                    parameterListOf(
                        FmtType("text/plain"),
                        XParameter("FILENAME", "file.ext"),
                    ),
                    ByteBuffer.wrap(binary)
                )
            )
        )

        val result = builder.build(task)

        assertEquals(1, result.size)
        val subValue = result.first()
        assertEquals(JtxContract.JtxAttachment.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxAttachment.URI to null,
                JtxContract.JtxAttachment.FMTTYPE to "text/plain",
                JtxContract.JtxAttachment.FILENAME to "file.ext",
                JtxContract.JtxAttachment.OTHER to null
            ),
            subValue.values
        )
        assertContentEquals(binary, subValue.binaryData!!.array())
    }

    @Test
    fun `ATTACH without content`() {
        val task = VToDo(propertyListOf(Attach(ParameterList(), "")))

        val result = builder.build(task)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple ATTACH properties`() {
        val task = VToDo(
            propertyListOf(
                Attach(ParameterList(), URI.create("https://domain.example/file1.ext")),
                Attach(ParameterList(), URI.create("https://domain.example/file2.ext"))
            )
        )

        val result = builder.build(task)

        assertEquals(2, result.size)
        val subValueOne = result.first { "file1" in it.values.getAsString(JtxContract.JtxAttachment.URI) }
        assertEquals(JtxContract.JtxAttachment.CONTENT_URI, subValueOne.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxAttachment.URI to "https://domain.example/file1.ext",
                JtxContract.JtxAttachment.FMTTYPE to null,
                JtxContract.JtxAttachment.FILENAME to null,
                JtxContract.JtxAttachment.OTHER to null
            ),
            subValueOne.values
        )
        assertNull(subValueOne.binaryData)
        val subValueTwo = result.first { "file2" in it.values.getAsString(JtxContract.JtxAttachment.URI) }
        assertEquals(JtxContract.JtxAttachment.CONTENT_URI, subValueTwo.uri)
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxAttachment.URI to "https://domain.example/file2.ext",
                JtxContract.JtxAttachment.FMTTYPE to null,
                JtxContract.JtxAttachment.FILENAME to null,
                JtxContract.JtxAttachment.OTHER to null
            ),
            subValueTwo.values
        )
        assertNull(subValueTwo.binaryData)
    }
}
