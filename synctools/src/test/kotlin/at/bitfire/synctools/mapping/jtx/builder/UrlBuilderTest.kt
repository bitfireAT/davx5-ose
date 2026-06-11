/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.propertyListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class UrlBuilderTest {

    private val builder = UrlBuilder()

    @Test
    fun `No URL`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.URL))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.URL))
    }

    @Test
    fun `URL has value`() {
        val task = VToDo(propertyListOf(Url(URI.create("https://example.com"))))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals("https://example.com", output.entityValues.get(JtxContract.JtxICalObject.URL))
    }

    @Test
    fun `Invalid URL is handled`() {
        val task = VToDo(propertyListOf(Url(URI.create("invalid-url"))))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals("invalid-url", output.entityValues.get(JtxContract.JtxICalObject.URL))
    }
}
