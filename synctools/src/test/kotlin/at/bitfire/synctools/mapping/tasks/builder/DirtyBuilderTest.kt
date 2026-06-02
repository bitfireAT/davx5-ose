/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DirtyBuilderTest {

    private val builder = DirtyBuilder()

    @Test
    fun `old DIRTY is set to 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._DIRTY to 0
        ), result.entityValues)
    }

    @Test
    fun `DIRTY is set to 0`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._DIRTY to 0
        ), result.entityValues)
    }

}
