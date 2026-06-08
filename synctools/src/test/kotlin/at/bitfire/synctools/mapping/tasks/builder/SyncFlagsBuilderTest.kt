/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.COLUMN_FLAGS
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncFlagsBuilderTest {

    @Test
    fun `old Flags set to 123`() {
        val result = Entity(ContentValues())
        SyncFlagsBuilder(123).build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_FLAGS to 123
        ), result.entityValues)
    }

    @Test
    fun `Flags set to 123`() {
        val result = Entity(ContentValues())
        SyncFlagsBuilder(123).build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_FLAGS to 123
        ), result.entityValues)
    }

}
