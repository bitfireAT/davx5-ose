/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Created
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CreatedBuilderTest {

    private val builder = CreatedBuilder()

    @Test
    fun `old No CREATED stores null`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CREATED to null
        ), result.entityValues)
    }

    @Test
    fun `old CREATED is stored`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(createdAt = 1593771404L),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CREATED to 1593771404L
        ), result.entityValues)
    }

    @Test
    fun `No CREATED stores null`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CREATED to null
        ), result.entityValues)
    }

    @Test
    fun `CREATED is stored`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Created(null, Instant.ofEpochMilli(1593771404L))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CREATED to 1593771404L
        ), result.entityValues)
    }

}
