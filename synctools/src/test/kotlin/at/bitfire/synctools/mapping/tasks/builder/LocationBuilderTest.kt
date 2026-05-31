/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Location
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationBuilderTest {

    private val builder = LocationBuilder()

    @Test
    fun `old No LOCATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.LOCATION))
        assertNull(result.entityValues.get(Tasks.LOCATION))
    }

    @Test
    fun `old LOCATION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(location = ""),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.LOCATION))
        assertNull(result.entityValues.get(Tasks.LOCATION))
    }

    @Test
    fun `old LOCATION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(location = "Task Location"),
            to = result
        )
        assertEquals("Task Location", result.entityValues.getAsString(Tasks.LOCATION))
    }

    @Test
    fun `No LOCATION`() {
        val result = VToDo()
        builder.build(
            from = Task(),
            to = result
        )
        val location = result.getProperty<Location>(Location.LOCATION)
        assertTrue(location.isPresent)
        assertNull(location.get().value)
    }

    @Test
    fun `LOCATION is blank`() {
        val result = VToDo()
        builder.build(
            from = Task(location = ""),
            to = result
        )
        val location = result.getProperty<Location>(Location.LOCATION)
        assertTrue(location.isPresent)
        assertNull(location.get().value)
    }

    @Test
    fun `LOCATION is text`() {
        val result = VToDo()
        builder.build(
            from = Task(location = "Task Location"),
            to = result
        )
        assertEquals("Task Location", result.location.value)
    }

}
