/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreatedBuilderTest {

    private val builder = CreatedBuilder()

    @Test
    fun `No CREATED stores null`() {
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
    fun `CREATED is stored`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(createdAt = 1593771404L),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CREATED to 1593771404L
        ), result.entityValues)
    }

}
