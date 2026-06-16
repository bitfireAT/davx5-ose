/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class UrlHandlerTest {

    private val handler = UrlHandler()


    @Test
    fun `No URL`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.getProperty<Url>(Property.URL).getOrNull())
    }

    @Test
    fun `URL set`() {
        val input = Entity(contentValuesOf(Tasks.URL to "https://example.com"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val actual = task.getProperty<Url>(Property.URL).getOrNull()?.uri
        assertEquals(URI("https://example.com"), actual)
    }

}
