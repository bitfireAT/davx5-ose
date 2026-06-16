/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.mapping.tasks.VToDoUtil.build
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DescriptionBuilderTest {

    private val builder = DescriptionBuilder()


    @Test
    fun `No DESCRIPTION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.DESCRIPTION))
        assertNull(result.entityValues.get(Tasks.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = build(Description("")),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.DESCRIPTION))
        assertNull(result.entityValues.get(Tasks.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = build(Description("Task Details")),
            to = result
        )
        assertEquals("Task Details", result.entityValues.getAsString(Tasks.DESCRIPTION))
    }

}
