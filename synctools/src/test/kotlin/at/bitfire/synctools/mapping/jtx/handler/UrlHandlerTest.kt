/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class UrlHandlerTest {

    private val handler = UrlHandler()

    @Test
    fun `No URL`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.url)
    }

    @Test
    fun `URL with valid value`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.URL to "https://example.com"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Url(URI("https://example.com")), output.url)
    }

    @Test
    fun `URL with invalid value is ignored`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.URL to "not a valid uri ://"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.url)
    }
}
