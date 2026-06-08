/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Priority
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PriorityBuilderTest {

    private val builder = PriorityBuilder()


    @Test
    fun `No PRIORITY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PRIORITY to Priority.VALUE_UNDEFINED
        ), result.entityValues)
    }

    @Test
    fun `PRIORITY is 5`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Priority(5)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PRIORITY to 5
        ), result.entityValues)
    }

}
