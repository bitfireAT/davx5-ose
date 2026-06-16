/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Sequence
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceBuilderTest {

    private val builder = SequenceBuilder()


    @Test
    fun `No SEQUENCE defaults to 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.SYNC_VERSION to 0
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE is 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Sequence(0)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.SYNC_VERSION to 0
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE is 1`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Sequence(1)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.SYNC_VERSION to 1
        ), result.entityValues)
    }

}
