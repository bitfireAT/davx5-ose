/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.mapping.tasks.VToDoUtil
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
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.LOCATION))
        assertNull(result.entityValues.get(Tasks.LOCATION))
    }

    @Test
    fun `LOCATION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Location("")),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.LOCATION))
        assertNull(result.entityValues.get(Tasks.LOCATION))
    }

    @Test
    fun `LOCATION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Location("Task Location")),
            to = result
        )
        assertEquals("Task Location", result.entityValues.getAsString(Tasks.LOCATION))
    }

}
