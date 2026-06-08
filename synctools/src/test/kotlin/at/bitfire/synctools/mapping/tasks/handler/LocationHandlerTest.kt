/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Location
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationHandlerTest {

    private val handler = LocationHandler()


    @Test
    fun `No LOCATION`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.location)
    }

    @Test
    fun `LOCATION set`() {
        val input = Entity(contentValuesOf(Tasks.LOCATION to "Vienna, Austria"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Location("Vienna, Austria"), task.location)
    }
}
