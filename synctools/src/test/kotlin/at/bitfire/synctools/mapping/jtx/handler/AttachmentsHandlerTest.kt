/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.parameterListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attach
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class AttachmentsHandlerTest {

    private val attachmentFetcher = FakeAttachmentFetcher()
    private val handler = AttachmentsHandler(attachmentFetcher)

    @Test
    fun `no attachment sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Attach>(Property.ATTACH).size)
    }

    @Test
    fun `URI attachment`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(
                JtxContract.JtxAttachment.CONTENT_URI, contentValuesOf(
                    JtxContract.JtxAttachment.URI to "https://domain.example/file.txt",
                    JtxContract.JtxAttachment.FMTTYPE to "text/plain",
                    JtxContract.JtxAttachment.FILENAME to "file.txt",
                    JtxContract.JtxAttachment.OTHER to """{"key":"value"}"""
                )
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            Attach(
                parameterListOf(
                    FmtType("text/plain"),
                    XParameter("FILENAME", "file.txt"),
                    XParameter("X-LABEL", "file.txt"),
                    XParameter("key", "value")
                ),
                URI.create("https://domain.example/file.txt")
            ),
            output.getRequiredProperty<Attach>(Property.ATTACH)
        )
    }

    @Test
    fun `binary attachment`() {
        attachmentFetcher.attachmentData = "Binary attachment data".toByteArray()
        val input = Entity(ContentValues()).apply {
            addSubValue(
                JtxContract.JtxAttachment.CONTENT_URI, contentValuesOf(
                    JtxContract.JtxAttachment.URI to "content://fakedata",
                    JtxContract.JtxAttachment.FMTTYPE to "text/plain",
                    JtxContract.JtxAttachment.FILENAME to "file.txt"
                )
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(
            "ATTACH;ENCODING=BASE64;VALUE=BINARY;FMTTYPE=text/plain;FILENAME=file.txt;X-LABEL=file.txt:" +
                    "QmluYXJ5IGF0dGFjaG1lbnQgZGF0YQ==\r\n",
            output.getRequiredProperty<Attach>(Property.ATTACH).toString()
        )
        assertEquals("content://fakedata", attachmentFetcher.lastUri)
    }

    @Test
    fun `binary attachment that can't be fetched should be skipped`() {
        attachmentFetcher.attachmentData = null
        val input = Entity(ContentValues()).apply {
            addSubValue(
                JtxContract.JtxAttachment.CONTENT_URI,
                contentValuesOf(
                    JtxContract.JtxAttachment.URI to "content://fakedata/failing"
                )
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Attach>(Property.ATTACH).size)
        assertEquals("content://fakedata/failing", attachmentFetcher.lastUri)
    }

    @Test
    fun `attachment with invalid URI should be skipped`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(
                JtxContract.JtxAttachment.CONTENT_URI,
                contentValuesOf(
                    JtxContract.JtxAttachment.URI to "invalid uri"
                )
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Attach>(Property.ATTACH).size)
    }
}

private class FakeAttachmentFetcher : AttachmentFetcher {
    var lastUri: String? = null
        private set

    var attachmentData: ByteArray? = null

    override fun getAttachmentData(uri: String): ByteArray? {
        lastUri = uri
        return attachmentData
    }
}
