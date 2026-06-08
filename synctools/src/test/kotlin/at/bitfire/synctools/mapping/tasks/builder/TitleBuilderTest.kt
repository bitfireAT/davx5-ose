/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Summary
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleBuilderTest {

    private val builder = TitleBuilder()

    @Test
    fun `old No SUMMARY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.TITLE))
        assertNull(result.entityValues.get(Tasks.TITLE))
    }

    @Test
    fun `old SUMMARY is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(summary = ""),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.TITLE))
        assertNull(result.entityValues.get(Tasks.TITLE))
    }

    @Test
    fun `old SUMMARY is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(summary = "Task Summary"),
            to = result
        )
        assertEquals("Task Summary", result.entityValues.getAsString(Tasks.TITLE))
    }

    @Test
    fun `No SUMMARY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.TITLE))
        assertNull(result.entityValues.get(Tasks.TITLE))
    }

    @Test
    fun `SUMMARY is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Summary("")),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.TITLE))
        assertNull(result.entityValues.get(Tasks.TITLE))
    }

    @Test
    fun `SUMMARY is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Summary("Task Summary")),
            to = result
        )
        assertEquals("Task Summary", result.entityValues.getAsString(Tasks.TITLE))
    }

}
