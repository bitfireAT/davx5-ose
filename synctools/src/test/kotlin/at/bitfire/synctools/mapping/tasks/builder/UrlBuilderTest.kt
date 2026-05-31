/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlBuilderTest {

    private val builder = UrlBuilder()

    @Test
    fun `old No URL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.URL to null
        ), result.entityValues)
    }

    @Test
    fun `old URL is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(url = "https://example.com"),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.URL to "https://example.com"
        ), result.entityValues)
    }


    @Test
    fun `No URL`() {
        val result = VToDo()
        builder.build(
            from = Task(),
            to = result
        )
        val url = result.getProperty<Url>(Url.URL)
        assertTrue(url.isPresent)
        assertNull(url.get().value.trimToNull())
    }

    @Test
    fun `URL is set`() {
        val result = VToDo()
        builder.build(
            from = Task(url = "https://example.com"),
            to = result
        )
        assertEquals("https://example.com", result.url.value)
    }

}
